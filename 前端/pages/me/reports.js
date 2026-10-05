/**
 * pages/me/reports —— 举报中心（提交举报 + 我的举报记录 + 申诉）
 *
 * 来源：
 *   《10》FR-M7-03 举报（原因 + 证据材料）/ FR-M7-06 我的举报记录与申诉
 *   《13》§3.7 POST /reports、GET /reports/my、POST /reports/{id}/appeal
 *   《18》内容治理：一级机审 → 人工复审 → 处罚 → 申诉闭环
 *
 * 双模式：
 *   - ?action=create&targetType=X&targetId=Y：举报表单
 *   - 无参：我的举报记录（可申诉）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const TARGET_TYPE = {
  1: '动态',
  2: '商品',
  3: '活动',
  4: '竞赛',
  5: '搭子帖',
  6: '评论',
  8: '用户',
  10: '订单'
};

Page({
  data: {
    mode: 'list',          // create | list
    targetType: null,
    targetId: null,
    targetTypeText: '',

    reasons: C.REPORT_REASON,
    reasonIndex: -1,
    description: '',
    evidences: [],
    submitting: false,

    list: [],
    page: 1,
    hasMore: true,
    loading: false,
    loaded: false,
    error: false
  },

  onLoad(query) {
    const q = query || {};
    if (q.action === 'create') {
      const tType = Number(q.targetType) || null;
      this.setData({
        mode: 'create',
        targetType: tType,
        targetId: q.targetId || null,
        targetTypeText: TARGET_TYPE[tType] || '内容'
      });
      wx.setNavigationBarTitle({ title: '举报' });
    } else {
      this.setData({ mode: 'list' });
      wx.setNavigationBarTitle({ title: '我的举报' });
    }
  },

  onShow() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '举报' });
      return;
    }
    if (this.data.mode === 'list') this.reload();
  },

  onReachBottom() {
    if (this.data.mode === 'list' && this.data.hasMore) this.fetch(false);
  },

  onPullDownRefresh() {
    if (this.data.mode !== 'list') { wx.stopPullDownRefresh(); return; }
    this.reload().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  /* ------------------------------------------------------------------ */
  /* 举报表单                                                            */
  /* ------------------------------------------------------------------ */
  onPickReason(e) {
    this.setData({ reasonIndex: Number(e.currentTarget.dataset.index) });
  },

  onDescInput(e) {
    this.setData({ description: e.detail.value });
  },

  onAddEvidence() {
    const rest = 6 - this.data.evidences.length;
    if (rest <= 0) {
      util.toast('最多上传 6 张证据');
      return;
    }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const ok = res.tempFiles.filter(f => f.size <= 10 * 1024 * 1024).map(f => f.tempFilePath);
        this.setData({ evidences: this.data.evidences.concat(ok) });
      }
    });
  },

  onDeleteEvidence(e) {
    const i = Number(e.currentTarget.dataset.index);
    this.setData({ evidences: this.data.evidences.filter((_, idx) => idx !== i) });
  },

  onPreviewEvidence(e) {
    const i = Number(e.currentTarget.dataset.index);
    wx.previewImage({ urls: this.data.evidences, current: this.data.evidences[i] });
  },

  onSubmit() {
    if (this.data.submitting) return;
    if (this.data.reasonIndex < 0) {
      util.toast('请选择举报原因');
      return;
    }
    if (this.data.description.length > 300) {
      util.toast('补充说明不能超过 300 字');
      return;
    }
    if (!this.data.targetId) {
      util.toast('缺少举报对象');
      return;
    }

    const reason = this.data.reasons[this.data.reasonIndex];
    this.setData({ submitting: true });
    util.toast('提交中', 'loading', 5000);

    this.uploadEvidences().then((urls) => {
      return api.report.create({
        targetType: this.data.targetType,
        targetId: this.data.targetId,
        reason: reason.value,
        description: this.data.description.trim(),
        evidences: urls
      });
    }).then(() => {
      wx.hideToast();
      this.setData({ submitting: false });
      wx.showModal({
        title: '举报已提交',
        content: '我们会尽快核实处理，处理结果将通过站内消息通知你。',
        showCancel: false,
        confirmColor: '#1E6FE8',
        success: () => wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/home/index' }) })
      });
    }).catch((err) => {
      wx.hideToast();
      this.setData({ submitting: false });
      if (err && err.code === 40006) util.toast('你已举报过该内容，请勿重复提交');
    });
  },

  uploadEvidences() {
    const local = this.data.evidences.filter(p => !/^https?:/.test(p));
    if (!local.length) return Promise.resolve(this.data.evidences);
    return Promise.all(local.map(p => api.file.upload(p, C.FILE_BIZ.REPORT)))
      .then(results => results.map(r => (r && (r.url || (r.data && r.data.url))) || r));
  },

  /* ------------------------------------------------------------------ */
  /* 举报记录                                                            */
  /* ------------------------------------------------------------------ */
  reload() { return this.fetch(true); },

  fetch(reset) {
    if (this.data.loading) return Promise.resolve();
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, error: false });

    return api.report.my(page, 20).then((data) => {
      const raw = (data && data.list) || [];
      const incoming = raw.map(decorate);
      this.setData({
        list: reset ? incoming : this.data.list.concat(incoming),
        page,
        hasMore: raw.length >= 20,
        loading: false,
        loaded: true
      });
    }).catch(() => this.setData({ loading: false, loaded: true, error: true }));
  },

  onTapRecord(e) {
    const item = e.currentTarget.dataset.item;
    if (item.status === 3 || item.status === 2) {
      this.showDetail(item);
    }
  },

  showDetail(item) {
    wx.showModal({
      title: '举报详情',
      content: `举报对象：${TARGET_TYPE[item.targetType] || '内容'}\n举报原因：${item.reasonText || '—'}\n处理状态：${item.statusText}\n${item.handleResult ? '处理结果：' + item.handleResult : ''}`,
      showCancel: item.status === 3,
      cancelText: '关闭',
      confirmText: item.status === 3 ? '申诉' : '知道了',
      confirmColor: item.status === 3 ? '#F79009' : '#1E6FE8',
      success: (res) => {
        if (res.confirm && item.status === 3) this.doAppeal(item);
      }
    });
  },

  doAppeal(item) {
    wx.showModal({
      title: '提交申诉',
      editable: true,
      placeholderText: '请说明申诉理由（至少 5 个字）',
      confirmColor: '#1E6FE8',
      success: (res) => {
        const content = (res.content || '').trim();
        if (!res.confirm) return;
        if (content.length < 5) { util.toast('申诉理由至少 5 个字'); return; }
        wx.showLoading({ title: '提交中', mask: true });
        api.report.appeal(item.id, { content }).then(() => {
          wx.hideLoading();
          util.toast('申诉已提交', 'success');
          this.reload();
        }).catch(() => wx.hideLoading());
      }
    });
  },

  onRetry() {
    this.setData({ error: false, loaded: false });
    this.reload();
  },

  onGoHome() {
    wx.switchTab({ url: '/pages/home/index' });
  }
});

function decorate(item) {
  return Object.assign({}, item, {
    statusText: C.REPORT_STATUS_TEXT[item.status] || '待处理',
    reasonText: C.labelOf(C.REPORT_REASON, item.reason, ''),
    typeText: TARGET_TYPE[item.targetType] || '内容',
    timeText: util.relativeTime(item.createdAt)
  });
}
