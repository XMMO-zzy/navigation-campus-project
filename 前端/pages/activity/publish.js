/**
 * pages/activity/publish —— 发布活动（仅认证用户）
 *
 * 来源：
 *   《10》FR-M5-01 发布活动（仅认证用户，服务端校验）
 *   《13》§3.5 POST /activities
 *   《12》§2.5 activity（title/category/cover/start_at/end_at/enroll_deadline/quota/place/requirement/rule）
 */

const api = require('../../framework/api');
const util = require('../../framework/util');
const auth = require('../../framework/auth');
const C = require('../../framework/constants');

const CATEGORIES = C.ACTIVITY_CATEGORY;

function today() {
  const d = new Date();
  const p = (n) => (n < 10 ? '0' + n : '' + n);
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function emptyForm() {
  return {
    title: '',
    category: '',
    cover: '',
    content: '',
    organizer: '',
    place: '',
    startDate: '',
    startTime: '',
    endDate: '',
    endTime: '',
    enrollDeadline: '',
    quota: '',
    requirement: '',
    rule: '',
    contact: ''
  };
}

Page({
  data: {
    categories: CATEGORIES,
    categoryLabels: CATEGORIES.map(c => c.label),
    categoryText: '',
    minDate: today(),
    form: emptyForm(),
    submitting: false
  },

  onLoad() {
    if (!auth.requireVerified({ title: '发布活动' })) {
      setTimeout(() => wx.navigateBack(), 800);
      return;
    }
    const draft = wx.getStorageSync('activity_publish_draft');
    if (draft && draft.savedAt && Date.now() - draft.savedAt < 7 * 24 * 3600 * 1000) {
      this.setData({
        form: Object.assign(emptyForm(), draft.form),
        categoryText: C.labelOf(CATEGORIES, draft.form.category, '')
      });
    }
  },

  onUnload() { this.saveDraft(); },

  saveDraft() {
    const f = this.data.form;
    if (!f.title && !f.content) return;
    wx.setStorageSync('activity_publish_draft', { form: f, savedAt: Date.now() });
  },

  /* ------------------------------------------------------------------ */
  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  onPickerCategory(e) {
    const hit = CATEGORIES[Number(e.detail.value)];
    if (!hit) return;
    this.setData({ 'form.category': hit.value, categoryText: hit.label });
  },

  onDateChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  onTimeChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  /* ---------- 封面 ---------- */
  onChooseCover() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        this.setData({ 'form.cover': res.tempFiles[0].tempFilePath });
      }
    });
  },

  onRemoveCover() { this.setData({ 'form.cover': '' }); },

  /* ------------------------------------------------------------------ */
  validate() {
    const f = this.data.form;
    if (!f.title || f.title.trim().length < 4) { util.toast('活动标题至少 4 个字'); return false; }
    if (f.title.trim().length > 60) { util.toast('活动标题不能超过 60 个字'); return false; }
    if (!f.category && f.category !== 0) { util.toast('请选择活动分类'); return false; }
    if (!f.cover) { util.toast('请上传活动封面'); return false; }
    if (!f.content || f.content.trim().length < 20) { util.toast('活动介绍至少 20 个字'); return false; }
    if (!f.place) { util.toast('请填写活动地点'); return false; }
    if (!f.startDate || !f.startTime) { util.toast('请选择活动开始时间'); return false; }
    if (!f.endDate || !f.endTime) { util.toast('请选择活动结束时间'); return false; }
    const start = new Date(`${f.startDate}T${f.startTime}:00`).getTime();
    const end = new Date(`${f.endDate}T${f.endTime}:00`).getTime();
    if (end <= start) { util.toast('结束时间必须晚于开始时间'); return false; }
    if (f.enrollDeadline) {
      const dl = new Date(f.enrollDeadline).getTime();
      if (dl > start) { util.toast('报名截止时间不能晚于活动开始时间'); return false; }
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
    return api.file.upload(cover, C.FILE_BIZ.ACTIVITY).then((r) => {
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
      return api.activity.create({
        title: f.title.trim(),
        category: Number(f.category),
        cover: cover,
        images: [cover],
        content: f.content.trim(),
        organizer: f.organizer.trim(),
        place: f.place.trim(),
        startAt: `${f.startDate} ${f.startTime}:00`,
        endAt: `${f.endDate} ${f.endTime}:00`,
        enrollDeadline: f.enrollDeadline ? `${f.enrollDeadline} 23:59:59` : null,
        quota: f.quota === '' ? null : Number(f.quota),
        requirement: f.requirement.trim(),
        rule: f.rule.trim(),
        contact: f.contact.trim()
      });
    }).then((res) => {
      this.setData({ submitting: false });
      wx.hideToast();
      wx.removeStorageSync('activity_publish_draft');
      util.toast('发布成功，等待内容审核', 'success');
      const id = res && (res.id || (res.data && res.data.id));
      setTimeout(() => wx.redirectTo({ url: `/pages/activity/detail?id=${id}` }), 900);
    }).catch((err) => {
      this.setData({ submitting: false });
      wx.hideToast();
      if (err && err.code === 40302) auth.requireVerified({ title: '发布活动' });
    });
  }
});
