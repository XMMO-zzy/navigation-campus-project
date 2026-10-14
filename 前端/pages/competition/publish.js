/**
 * pages/competition/publish —— 发布竞赛（仅认证用户）
 *
 * 来源：
 *   《10》FR-M5-03 发布竞赛（仅认证用户）
 *   《13》§3.5 POST /competitions
 *   《12》§2.5 competition（title/level/cover/organizer/start_at/end_at/enroll_deadline/quota/place）
 */

const api = require('../../framework/api');
const util = require('../../framework/util');
const auth = require('../../framework/auth');
const C = require('../../framework/constants');
const drafts = require('../../framework/draft');

const LEVELS = C.COMPETITION_LEVEL;

function pad(n) { return n < 10 ? '0' + n : '' + n; }

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function emptyForm() {
  return {
    title: '',
    level: '',
    cover: '',
    organizer: '',
    content: '',
    place: '',
    startDate: '',
    startTime: '',
    endDate: '',
    endTime: '',
    enrollDeadline: '',
    quota: '',
    requirement: '',
    awards: '',
    rule: '',
    contact: ''
  };
}

Page({
  data: {
    levels: LEVELS,
    levelLabels: LEVELS.map(l => l.label),
    levelText: '',
    minDate: today(),
    form: emptyForm(),
    submitting: false
  },

  onLoad() {
    if (!auth.requireVerified({ title: '发布竞赛' })) {
      setTimeout(() => wx.navigateBack(), 800);
      return;
    }
    const draft = drafts.load(drafts.TYPE.COMPETITION);
    if (draft && draft.form) {
      this.setData({
        form: Object.assign(emptyForm(), draft.form),
        levelText: C.labelOf(LEVELS, draft.form.level, '')
      });
    }
  },

  onUnload() { this.saveDraft(); },

  saveDraft() {
    const f = this.data.form;
    if (!f.title && !f.content) return;
    // 统一草稿层：本地立即写 + 服务端同步（FR-M2-10 / T2.6）
    drafts.save(drafts.TYPE.COMPETITION, { form: f }, true);
  },

  /* ------------------------------------------------------------------ */
  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  onPickerLevel(e) {
    const hit = LEVELS[Number(e.detail.value)];
    if (!hit) return;
    this.setData({ 'form.level': hit.value, levelText: hit.label });
  },

  onDateChange(e) {
    this.setData({ ['form.' + e.currentTarget.dataset.field]: e.detail.value });
  },

  onTimeChange(e) {
    this.setData({ ['form.' + e.currentTarget.dataset.field]: e.detail.value });
  },

  onChooseCover() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => this.setData({ 'form.cover': res.tempFiles[0].tempFilePath })
    });
  },

  onRemoveCover() { this.setData({ 'form.cover': '' }); },

  /* ------------------------------------------------------------------ */
  validate() {
    const f = this.data.form;
    if (!f.title || f.title.trim().length < 4) { util.toast('竞赛名称至少 4 个字'); return false; }
    if (f.title.trim().length > 60) { util.toast('竞赛名称不能超过 60 个字'); return false; }
    if (!f.level && f.level !== 0) { util.toast('请选择竞赛级别'); return false; }
    if (!f.cover) { util.toast('请上传竞赛封面'); return false; }
    if (!f.organizer) { util.toast('请填写主办单位'); return false; }
    if (!f.content || f.content.trim().length < 20) { util.toast('竞赛介绍至少 20 个字'); return false; }
    if (!f.startDate || !f.startTime) { util.toast('请选择比赛开始时间'); return false; }
    if (!f.endDate || !f.endTime) { util.toast('请选择比赛结束时间'); return false; }
    const start = new Date(`${f.startDate}T${f.startTime}:00`).getTime();
    const end = new Date(`${f.endDate}T${f.endTime}:00`).getTime();
    if (end <= start) { util.toast('结束时间必须晚于开始时间'); return false; }
    if (f.enrollDeadline) {
      const dl = new Date(`${f.enrollDeadline}T23:59:59`).getTime();
      if (dl > start) { util.toast('报名截止时间不能晚于比赛开始时间'); return false; }
    }
    if (f.quota !== '' && (isNaN(Number(f.quota)) || Number(f.quota) < 1)) {
      util.toast('请填写正确的名额数量');
      return false;
    }
    if (!f.contact) { util.toast('请填写联系方式'); return false; }
    return true;
  },

  uploadCover() {
    const cover = this.data.form.cover;
    if (!cover || /^https?:/.test(cover)) return Promise.resolve(cover);
    wx.showLoading({ title: '上传封面中', mask: true });
    return api.file.upload(cover, C.FILE_BIZ.COMPETITION).then((r) => {
      wx.hideLoading();
      const url = (r && (r.url || (r.data && r.data.url))) || r;
      this.setData({ 'form.cover': url });
      return url;
    }).catch((err) => { wx.hideLoading(); throw err; });
  },

  onSubmit() {
    if (this.data.submitting) return;
    if (!this.validate()) return;

    const f = this.data.form;
    this.setData({ submitting: true });
    util.toast('提交中', 'loading', 2000);

    this.uploadCover().then((cover) => {
      return api.competition.create({
        title: f.title.trim(),
        level: Number(f.level),
        cover: cover,
        images: [cover],
        organizer: f.organizer.trim(),
        content: f.content.trim(),
        place: f.place.trim(),
        startAt: `${f.startDate} ${f.startTime}:00`,
        endAt: `${f.endDate} ${f.endTime}:00`,
        enrollDeadline: f.enrollDeadline ? `${f.enrollDeadline} 23:59:59` : null,
        quota: f.quota === '' ? null : Number(f.quota),
        requirement: f.requirement.trim(),
        awards: f.awards.trim(),
        rule: f.rule.trim(),
        contact: f.contact.trim()
      });
    }).then((res) => {
      this.setData({ submitting: false });
      wx.hideToast();
      drafts.clear(drafts.TYPE.COMPETITION);
      util.toast('发布成功，等待内容审核', 'success');
      const id = res && (res.id || (res.data && res.data.id));
      setTimeout(() => wx.redirectTo({ url: `/pages/competition/detail?id=${id}` }), 900);
    }).catch((err) => {
      this.setData({ submitting: false });
      wx.hideToast();
      if (err && err.code === 40302) auth.requireVerified({ title: '发布竞赛' });
    });
  }
});
