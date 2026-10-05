/**
 * pages/team/apply-center —— 组队申请中心
 *
 * 来源：
 *   《10》FR-M3-04 组队申请中心（收到的申请 / 我的申请 / 收到的邀请）
 *   《13》§3.3 GET /team-applies?direction=received|sent|invited、
 *              PUT /team-applies/{id}  { action: agree|reject|ask|withdraw, message }
 *   《12》§2.3 team_apply.status(0 待处理/1 已通过/2 已拒绝/3 已撤回/4 补充询问)
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const TABS = C.APPLY_DIRECTION;

Page({
  data: {
    tabs: TABS,
    direction: 'received',

    list: [],
    page: 1,
    hasMore: true,
    loading: true,
    loadStatus: '',
    loaded: false,
    error: false,

    showAsk: false,
    askApply: null,
    askMessage: '',
    sending: false
  },

  onLoad(query) {
    const direction = (query && ['received', 'sent', 'invited'].indexOf(query.direction) > -1)
      ? query.direction : 'received';
    this.setData({ direction });
    if (!auth.requireAuth({ title: '查看申请需要先登录' })) {
      this.setData({ loading: false, loaded: true });
      return;
    }
    this.load(true);
  },

  onShow() {
    if (this._needRefresh) { this._needRefresh = false; this.load(true); }
  },

  onPullDownRefresh() {
    this.load(true).then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  onReachBottom() { this.loadMore(); },

  load(reset) {
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: reset, loadStatus: reset ? '' : 'loading', error: false });
    return api.team.applies(this.data.direction, page, 20).then((data) => {
      const raw = (data && data.list) || [];
      const incoming = raw.map(decorate);
      this.setData({
        list: reset ? incoming : this.data.list.concat(incoming),
        page,
        hasMore: raw.length >= 20,
        loading: false,
        loaded: true,
        loadStatus: raw.length >= 20 ? '' : 'nomore'
      });
    }).catch(() => this.setData({ loading: false, loaded: true, error: true }));
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading) return;
    this.load(false);
  },

  /* ------------------------------------------------------------------ */
  onSwitchTab(e) {
    const direction = e.currentTarget.dataset.key;
    if (direction === this.data.direction) return;
    this.setData({ direction, page: 1, list: [] }, () => this.load(true));
  },

  /* ------------------------------------------------------------------ */
  /* 收到的申请 / 邀请：同意 / 拒绝 / 补充询问                              */
  /* ------------------------------------------------------------------ */
  onAgree(e) {
    const id = e.currentTarget.dataset.id;
    util.confirm('同意后将加入该队伍，确定同意？', '同意申请', '同意').then((ok) => {
      if (!ok) return;
      this.handleApply(id, 'agree');
    });
  },

  onReject(e) {
    const id = e.currentTarget.dataset.id;
    util.confirm('确定拒绝该申请？', '拒绝申请', '拒绝').then((ok) => {
      if (!ok) return;
      this.handleApply(id, 'reject');
    });
  },

  onOpenAsk(e) {
    const id = e.currentTarget.dataset.id;
    const apply = this.data.list.filter(i => String(i.id) === String(id))[0];
    this.setData({ showAsk: true, askApply: apply || { id }, askMessage: '' });
  },

  onCloseAsk() { this.setData({ showAsk: false, askApply: null }); },

  onAskInput(e) { this.setData({ askMessage: e.detail.value }); },

  onSubmitAsk() {
    if (this.data.sending) return;
    const msg = this.data.askMessage.trim();
    if (msg.length < 2) { util.toast('请输入你想补充询问的内容'); return; }
    this.setData({ sending: true });
    api.team.handleApply(this.data.askApply.id, 'ask', msg).then(() => {
      this.setData({ sending: false, showAsk: false, askApply: null });
      util.toast('已发送补充询问', 'success');
      this.load(true);
    }).catch(() => this.setData({ sending: false }));
  },

  /** 我的申请：撤回 */
  onWithdraw(e) {
    const id = e.currentTarget.dataset.id;
    util.confirm('撤回后对方将不再看到该申请，确定撤回？', '撤回申请', '撤回').then((ok) => {
      if (!ok) return;
      this.handleApply(id, 'withdraw');
    });
  },

  handleApply(id, action) {
    wx.showLoading({ title: '处理中', mask: true });
    api.team.handleApply(id, action).then(() => {
      wx.hideLoading();
      const tip = { agree: '已同意', reject: '已拒绝', withdraw: '已撤回' }[action] || '处理完成';
      util.toast(tip, 'success');
      this.load(true);
    }).catch(() => wx.hideLoading());
  },

  /* ------------------------------------------------------------------ */
  onTapTeam(e) {
    const id = e.currentTarget.dataset.teamid;
    if (!id) return;
    wx.navigateTo({ url: `/pages/team/detail?id=${id}` });
  },

  onTapUser(e) {
    const uid = e.currentTarget.dataset.uid;
    if (!uid) return;
    wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${uid}` });
  },

  onRetry() { this.load(!this.data.list.length); },

  onLogin() { auth.requireAuth({ title: '登录后查看' }); },
  noop() {}
});

function decorate(item) {
  const st = item.status === undefined ? 0 : item.status;
  return Object.assign({}, item, {
    statusText: C.APPLY_STATUS_TEXT[st] || '待处理',
    statusTag: C.APPLY_STATUS_TAG[st] || 'warning',
    timeText: util.relativeTime(item.createdAt || item.applyAt),
    applicant: item.applicant || item.user || {},
    teamName: item.teamName || (item.team && item.team.name) || `队伍 #${item.teamId || '—'}`
  });
}
