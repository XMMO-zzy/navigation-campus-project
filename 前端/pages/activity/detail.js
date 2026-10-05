/**
 * pages/activity/detail —— 活动详情 + 报名
 *
 * 来源：
 *   《10》FR-M5-02 活动详情 / FR-M5-05 活动报名（重复 40006、截止 40007）
 *   《13》§3.5 GET /activities/{id}、POST /activities/{id}/enroll
 *   《12》§2.5 activity_enroll.status(0 待审核/1 已通过/2 已拒绝/3 已签到/4 已取消)
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

Page({
  data: {
    id: null,
    loading: true,
    error: false,
    act: null,
    isOwner: false,
    myEnroll: null,
    enrollStatusText: '',
    enrollStatusTag: '',
    showEnroll: false,
    enrollForm: { name: '', phone: '', remark: '' },
    enrolling: false,
    showReview: false,
    enrollList: [],
    reviewLoading: false
  },

  onLoad(query) {
    const id = query && query.id;
    if (!id) { this.setData({ loading: false, error: true }); return; }
    this.setData({ id });
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  load() {
    return api.activity.detail(this.data.id).then((d) => {
      const me = auth.currentUser() || {};
      const statusMap = { 0: '待开始', 1: '报名中', 2: '进行中', 3: '已结束', 4: '已取消' };
      const statusTag = { 0: 'info', 1: 'success', 2: 'warning', 3: 'gray', 4: 'gray' };
      const act = Object.assign({}, d, {
        categoryText: C.labelOf(C.ACTIVITY_CATEGORY, d.category, '活动'),
        statusText: statusMap[d.status] !== undefined ? statusMap[d.status] : '报名中',
        statusTag: statusTag[d.status] || 'info',
        cover: d.cover || (Array.isArray(d.images) && d.images[0]) || '/static/placeholder.png',
        images: d.images || (d.cover ? [d.cover] : []),
        startText: util.formatTime(d.startAt || d.startTime, 'full'),
        endText: util.formatTime(d.endAt || d.endTime, 'full'),
        deadlineText: util.formatTime(d.enrollDeadline, 'full'),
        enrolled: d.enrolledCount || d.enrollCount || 0,
        quotaText: d.quota ? `${d.enrolledCount || 0}/${d.quota}` : `${d.enrolledCount || 0} 人`,
        leftText: ''
      });
      if (d.quota) {
        const left = Math.max(0, Number(d.quota) - Number(act.enrolled));
        act.leftText = left > 0 ? `剩余 ${left} 个名额` : '名额已满';
      }
      const enroll = d.myEnroll || d.myEnrollStatus !== undefined
        ? { status: d.myEnrollStatus, ...(d.myEnroll || {}) }
        : null;
      this.setData({
        loading: false,
        error: false,
        act,
        isOwner: !!(me.userId && String(d.userId || d.creatorId) === String(me.userId)),
        myEnroll: enroll && enroll.status !== undefined ? enroll : null,
        enrollStatusText: enroll ? (C.ENROLL_STATUS_TEXT[enroll.status] || '') : '',
        enrollStatusTag: enroll ? (C.ENROLL_STATUS_TAG[enroll.status] || 'info') : ''
      });
    }).catch(() => this.setData({ loading: false, error: true }));
  },

  onRetry() { this.setData({ loading: true, error: false }); this.load(); },

  /* ------------------------------------------------------------------ */
  onPreviewCover() {
    const urls = (this.data.act && this.data.act.images) || [];
    if (!urls.length) return;
    wx.previewImage({ current: urls[0], urls });
  },

  onTapOwner() {
    const a = this.data.act || {};
    const uid = a.userId || a.creatorId || (a.creator && a.creator.id);
    if (!uid) return;
    wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${uid}` });
  },

  /* ------------------------------------------------------------------ */
  /* 报名                                                                */
  /* ------------------------------------------------------------------ */
  onOpenEnroll() {
    if (!auth.requireAuth({ title: '报名需要先登录' })) return;
    if (!auth.requireVerified({ title: '活动报名' })) return;
    if (this.data.isOwner) { util.toast('你是本活动的组织者'); return; }
    if (this.data.myEnroll) {
      util.toast(`你已报名，当前状态：${this.data.enrollStatusText}`);
      return;
    }
    const a = this.data.act;
    if (a.status === 3 || a.status === 4) { util.toast('活动已结束或已取消'); return; }
    if (a.quota && a.enrolled >= a.quota) { util.toast('名额已满'); return; }
    this.setData({ showEnroll: true });
  },

  onCloseEnroll() { this.setData({ showEnroll: false }); },

  onEnrollInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['enrollForm.' + field]: e.detail.value });
  },

  onSubmitEnroll() {
    if (this.data.enrolling) return;
    const f = this.data.enrollForm;
    if (!f.name || f.name.trim().length < 2) { util.toast('请填写真实姓名'); return; }
    if (!/^1[3-9]\d{9}$/.test((f.phone || '').trim())) { util.toast('请填写正确的手机号'); return; }

    this.setData({ enrolling: true });
    api.activity.enroll(this.data.id, {
      name: f.name.trim(),
      phone: f.phone.trim(),
      remark: (f.remark || '').trim()
    }).then(() => {
      this.setData({
        enrolling: false,
        showEnroll: false,
        myEnroll: { status: 0 },
        enrollStatusText: C.ENROLL_STATUS_TEXT[0],
        enrollStatusTag: C.ENROLL_STATUS_TAG[0]
      });
      util.toast('报名成功，等待组织者审核', 'success');
      this.load();
    }).catch((err) => {
      this.setData({ enrolling: false });
      if (err && err.code === 40006) {
        util.toast('你已报名该活动');
        this.setData({ showEnroll: false });
        this.load();
      } else if (err && err.code === 40007) {
        util.toast('报名已截止');
        this.setData({ showEnroll: false });
      }
    });
  },

  /* ------------------------------------------------------------------ */
  /* 组织者：报名审核（FR-M5-02）                                          */
  /* ------------------------------------------------------------------ */
  onOpenReview() {
    const a = this.data.act || {};
    this.setData({ showReview: true, reviewLoading: true });
    // 说明：《13》§3.5 的报名审核列表接口未在契约中定义，
    //      此处复用详情返回的 enrolls 字段；若为空则展示空态，不臆造请求。
    const list = (a.enrolls || []).map((e) => Object.assign({}, e, {
      statusText: C.ENROLL_STATUS_TEXT[e.status] || '',
      statusTag: C.ENROLL_STATUS_TAG[e.status] || 'info'
    }));
    this.setData({ enrollList: list, reviewLoading: false });
  },

  onCloseReview() { this.setData({ showReview: false }); },

  onReview(e) {
    const { id, action } = e.currentTarget.dataset;
    const doReview = () => {
      api.activity.reviewEnroll(id, action).then(() => {
        util.toast(action === 'approve' ? '已通过' : '已拒绝', 'success');
        this.onOpenReview();
        this.load();
      });
    };
    if (action === 'reject') {
      util.confirm('确定拒绝该报名申请？', '拒绝报名', '拒绝').then(ok => { if (ok) doReview(); });
    } else {
      doReview();
    }
  },

  onTapTeam() {
    util.toast('组队功能请前往活动对应队伍页面');
  },

  onReport() {
    wx.navigateTo({ url: `/pages/me/reports?action=create&targetType=3&targetId=${this.data.id}` });
  },

  onShareAppMessage() {
    const a = this.data.act || {};
    return {
      title: a.title || '校园活动',
      path: `/pages/activity/detail?id=${this.data.id}`,
      imageUrl: a.cover || ''
    };
  },

  noop() {}
});
