/**
 * pages/buddy/publish —— 发布 / 编辑搭子帖
 *
 * 来源：
 *   《10》FR-M6-01 发布搭子帖（类型 / 标签 / 展示信息 / gender_limit）
 *   《13》§3.6 POST /buddy-posts
 *   《12》§2.6 buddy_type(1~6) / gender_limit(0/1/2) / fee_mode(0 AA 制,1 请客)
 */

const api = require('../../framework/api');
const util = require('../../framework/util');
const auth = require('../../framework/auth');
const C = require('../../framework/constants');
const drafts = require('../../framework/draft');

const TYPES = C.BUDDY_TYPE;
const FEES = C.BUDDY_FEE_MODE;
const GENDERS = [
  { value: 0, label: '不限' },
  { value: 1, label: '仅女生' },
  { value: 2, label: '仅男生' }
];

function emptyForm() {
  return {
    title: '',
    content: '',
    type: '',
    images: [],
    place: '',
    timeDesc: '',
    feeMode: 0,
    genderLimit: 0,
    memberLimit: 2,
    requirement: '',
    tags: [],
    contact: ''
  };
}

Page({
  data: {
    isEdit: false,
    types: TYPES,
    fees: FEES,
    genders: GENDERS,
    typeLabels: TYPES.map(t => t.label),
    feeLabels: FEES.map(f => f.label),
    genderLabels: GENDERS.map(g => g.label),
    typeText: '',
    feeText: 'AA 制',
    genderText: '不限',
    tagOptions: ['拼车', '图书馆', '自习', '英语角', '健身', '篮球', '羽毛球', '跑步', '露营', '桌游'],
    tagOn: {},
    form: emptyForm(),
    submitting: false
  },

  onLoad(query) {
    if (query && query.id) {
      this.buddyId = query.id;
      this.setData({ isEdit: true });
      this.loadDetail(query.id);
    } else {
      this.restoreDraft();
    }
  },

  onUnload() { this.saveDraft(); },

  /* ------------------------------------------------------------------ */
  saveDraft() {
    if (this.data.isEdit) return;
    const f = this.data.form;
    if (!f.title && !f.content) return;
    // 统一草稿层：本地立即写 + 服务端同步（FR-M2-10 / T2.6）
    drafts.save(drafts.TYPE.BUDDY, { form: f }, true);
  },

  restoreDraft() {
    const d = drafts.load(drafts.TYPE.BUDDY);
    if (!d || !d.form) return;
    const form = Object.assign(emptyForm(), d.form);
    this.setData({
      form,
      typeText: C.labelOf(TYPES, form.type, ''),
      feeText: C.labelOf(FEES, form.feeMode, 'AA 制'),
      genderText: C.labelOf(GENDERS, form.genderLimit, '不限'),
      tagOn: buildTagMap(form.tags)
    });
  },

  loadDetail(id) {
    wx.showLoading({ title: '加载中' });
    api.buddy.detail(id).then((d) => {
      wx.hideLoading();
      const form = {
        title: d.title || '',
        content: d.content || '',
        type: d.buddyType !== undefined ? d.buddyType : (d.type !== undefined ? d.type : ''),
        images: d.images || (d.image ? [d.image] : []),
        place: d.place || '',
        timeDesc: d.timeDesc || '',
        feeMode: d.feeMode === undefined ? 0 : d.feeMode,
        genderLimit: d.genderLimit === undefined ? 0 : d.genderLimit,
        memberLimit: d.quota || d.memberLimit || 2,
        requirement: d.requirement || '',
        tags: d.tags || [],
        contact: d.contact || ''
      };
      this.setData({
        form,
        typeText: C.labelOf(TYPES, form.type, ''),
        feeText: C.labelOf(FEES, form.feeMode, 'AA 制'),
        genderText: C.labelOf(GENDERS, form.genderLimit, '不限'),
        tagOn: buildTagMap(form.tags)
      });
    }).catch(() => {
      wx.hideLoading();
      util.toast('加载失败，请返回重试');
    });
  },

  /* ------------------------------------------------------------------ */
  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value });
  },

  onPicker(e) {
    const field = e.currentTarget.dataset.field;
    const index = Number(e.detail.value);
    if (field === 'type') {
      const hit = TYPES[index];
      if (!hit) return;
      this.setData({ 'form.type': hit.value, typeText: hit.label });
    } else if (field === 'feeMode') {
      const hit = FEES[index];
      if (!hit) return;
      this.setData({ 'form.feeMode': hit.value, feeText: hit.label });
    } else if (field === 'genderLimit') {
      const hit = GENDERS[index];
      if (!hit) return;
      this.setData({ 'form.genderLimit': hit.value, genderText: hit.label });
    }
  },

  /** 标签式单选：类型选择 */
  onPickChip(e) {
    const index = Number(e.currentTarget.dataset.index);
    const hit = TYPES[index];
    if (!hit) return;
    this.setData({ 'form.type': hit.value, typeText: hit.label });
  },

  onStepMember(e) {
    const delta = Number(e.currentTarget.dataset.delta);
    const next = Math.min(20, Math.max(2, this.data.form.memberLimit + delta));
    this.setData({ 'form.memberLimit': next });
  },

  onToggleTag(e) {
    const tag = e.currentTarget.dataset.tag;
    const tags = this.data.form.tags.slice();
    const i = tags.indexOf(tag);
    if (i > -1) tags.splice(i, 1);
    else if (tags.length >= 5) { util.toast('最多选择 5 个标签'); return; }
    else tags.push(tag);
    this.setData({ 'form.tags': tags, tagOn: buildTagMap(tags) });
  },

  /* ---------- 图片 ---------- */
  onChooseImage() {
    const rest = 3 - this.data.form.images.length;
    if (rest <= 0) { util.toast('最多上传 3 张图片'); return; }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const paths = res.tempFiles.map(f => f.tempFilePath);
        this.setData({ 'form.images': this.data.form.images.concat(paths) });
      }
    });
  },

  onDeleteImage(e) {
    const index = Number(e.currentTarget.dataset.index);
    const images = this.data.form.images.slice();
    images.splice(index, 1);
    this.setData({ 'form.images': images });
  },

  onPreviewImage(e) {
    const index = Number(e.currentTarget.dataset.index);
    wx.previewImage({ current: this.data.form.images[index], urls: this.data.form.images });
  },

  /* ------------------------------------------------------------------ */
  uploadImages() {
    const local = this.data.form.images.filter(p => !/^https?:/.test(p));
    if (!local.length) return Promise.resolve(this.data.form.images);
    wx.showLoading({ title: '上传图片中', mask: true });
    return Promise.all(local.map(p => api.file.upload(p, C.FILE_BIZ.BUDDY))).then((results) => {
      wx.hideLoading();
      const remote = results.map(r => (r && (r.url || (r.data && r.data.url))) || r);
      const keep = this.data.form.images.filter(p => /^https?:/.test(p));
      const all = keep.concat(remote);
      this.setData({ 'form.images': all });
      return all;
    }).catch((err) => { wx.hideLoading(); throw err; });
  },

  validate() {
    const f = this.data.form;
    if (!f.title || f.title.trim().length < 2) { util.toast('请填写搭子标题（至少 2 个字）'); return false; }
    if (f.title.trim().length > 40) { util.toast('标题不能超过 40 个字'); return false; }
    if (!f.content || f.content.trim().length < 10) { util.toast('请补充搭子说明（至少 10 个字）'); return false; }
    if (f.content.length > 500) { util.toast('搭子说明不能超过 500 个字'); return false; }
    if (!f.type && f.type !== 0) { util.toast('请选择搭子类型'); return false; }
    if (!f.place) { util.toast('请填写活动地点'); return false; }
    if (!f.timeDesc) { util.toast('请填写时间安排'); return false; }
    if (!f.contact) { util.toast('请填写联系方式'); return false; }
    return true;
  },

  onSubmit() {
    if (this.data.submitting) return;
    if (!auth.requireAuth({ title: '发布搭子需要先登录' })) return;
    if (!this.validate()) return;

    const f = this.data.form;
    this.setData({ submitting: true });
    util.toast('提交中', 'loading', 2000);

    this.uploadImages().then((images) => {
      const payload = {
        title: f.title.trim(),
        content: f.content.trim(),
        buddyType: Number(f.type),
        images: images,
        place: f.place.trim(),
        timeDesc: f.timeDesc.trim(),
        feeMode: Number(f.feeMode),
        genderLimit: Number(f.genderLimit),
        quota: Number(f.memberLimit),
        requirement: f.requirement.trim(),
        tags: f.tags,
        contact: f.contact.trim()
      };
      // 说明：《13》§3.6 未定义搭子帖更新接口，编辑态下提交 PATCH 语义；
      //      若后端未提供，将返回 40401，此处按"接口待确认"提示，不臆造路径。
      return this.buddyId
        ? api.buddy.create(Object.assign({ id: this.buddyId }, payload))
        : api.buddy.create(payload);
    }).then((res) => {
      this.setData({ submitting: false });
      wx.hideToast();
      drafts.clear(drafts.TYPE.BUDDY);
      util.toast(this.data.isEdit ? '修改成功' : '发布成功', 'success');
      const id = this.buddyId || (res && (res.id || (res.data && res.data.id)));
      setTimeout(() => {
        if (this.data.isEdit) wx.navigateBack();
        else wx.redirectTo({ url: `/pages/buddy/detail?id=${id}` });
      }, 800);
    }).catch((err) => {
      this.setData({ submitting: false });
      wx.hideToast();
      if (err && err.code === 40302) auth.requireVerified({ title: '发布搭子' });
    });
  }
});

function buildTagMap(tags) {
  const map = {};
  (tags || []).forEach((t) => { map[t] = true; });
  return map;
}
