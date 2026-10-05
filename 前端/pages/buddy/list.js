/**
 * pages/buddy/list —— 找搭子列表
 *
 * 来源：
 *   《10》FR-M6-02 搭子列表（爱好筛选、女性模式）/ FR-M6-05 女性模式
 *   《13》§3.6 GET /buddy-posts?type=&keyword=&genderLimit=、GET /buddy-posts/my?view=
 *   《12》§2.6 buddy_type / gender_limit / fee_mode
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const VIEW_TABS = [
  { key: '', label: '全部搭子' },
  { key: 'published', label: '我发布的' },
  { key: 'favorite', label: '我收藏的' },
  { key: 'matched', label: '我滴过的' }
];

Page({
  data: {
    viewTabs: VIEW_TABS,
    view: '',
    types: [{ value: '', label: '全部类型' }].concat(C.BUDDY_TYPE),
    activeType: '',
    activeTypeText: '全部类型',
    genderLimit: false,
    femaleMode: false,

    list: [],
    page: 1,
    hasMore: true,
    loading: true,
    loadStatus: '',
    error: false,
    loaded: false
  },

  onLoad(query) {
    const view = (query && ['published', 'favorite', 'matched'].indexOf(query.view) > -1) ? query.view : '';
    const femaleMode = wx.getStorageSync('buddy_female_mode') === 1;
    this.setData({ view, femaleMode });
    this.load(true);
  },

  onShow() {
    if (this._needRefresh) { this._needRefresh = false; this.load(true); }
  },

  onPullDownRefresh() {
    this.load(true).then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  onReachBottom() { this.loadMore(); },

  /* ------------------------------------------------------------------ */
  load(reset) {
    if (this.data.view && !auth.isLogin()) {
      this.setData({ loading: false, loaded: true, list: [], hasMore: false });
      return Promise.resolve();
    }
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: reset, loadStatus: reset ? '' : 'loading', error: false });

    const req = this.data.view
      ? api.buddy.my(this.data.view, page, 20)
      : api.buddy.list({
          type: this.data.activeType || undefined,
          genderLimit: this.data.femaleMode ? 1 : undefined
        }, page, 20);

    return req.then((data) => {
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
  onSwitchView(e) {
    const view = e.currentTarget.dataset.key;
    if (view === this.data.view) return;
    if (view && !auth.requireAuth({ title: '查看需要先登录' })) return;
    this.setData({ view, page: 1 }, () => this.load(true));
  },

  onPickType(e) {
    const i = Number(e.detail.value);
    const hit = this.data.types[i];
    this.setData({ activeType: hit.value, activeTypeText: hit.label, page: 1 }, () => this.load(true));
  },

  /** 女性模式（FR-M6-05）：仅展示女生发起的搭子 */
  onToggleFemaleMode(e) {
    const on = e.detail.value;
    if (on && !auth.isLogin()) {
      this.setData({ femaleMode: false });
      auth.requireAuth({ title: '开启女性模式' });
      return;
    }
    wx.setStorageSync('buddy_female_mode', on ? 1 : 0);
    this.setData({ femaleMode: on, page: 1 }, () => this.load(true));
  },

  onTapItem(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/buddy/detail?id=${id}` });
  },

  onTapPublish() {
    if (!auth.requireAuth({ title: '发布搭子需要先登录' })) return;
    wx.navigateTo({ url: '/pages/buddy/publish' });
  },

  onRetry() { this.load(!this.data.list.length); },

  onLogin() { auth.requireAuth({ title: '登录后查看' }); }
});

function decorate(item) {
  return Object.assign({}, item, {
    typeText: C.labelOf(C.BUDDY_TYPE, item.buddyType || item.type, ''),
    feeText: C.labelOf(C.BUDDY_FEE_MODE, item.feeMode, 'AA 制'),
    timeText: util.relativeTime(item.createdAt),
    image: item.image || (Array.isArray(item.images) && item.images[0]) || '',
    genderText: item.genderLimit === 1 ? '限女生' : (item.genderLimit === 2 ? '限男生' : '不限性别'),
    joined: item.currentCount || item.joinedCount || 1,
    quota: item.quota || item.memberLimit || 2
  });
}
