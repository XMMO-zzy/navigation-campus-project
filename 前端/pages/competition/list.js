/**
 * pages/competition/list —— 竞赛列表 + 多维筛选
 *
 * 来源：
 *   《10》FR-M5-04 竞赛列表与多维筛选
 *   《13》§3.5 GET /competitions?level=&category=&status=&keyword=
 *   《12》§2.5 competition.level(1 校级/2 省级/3 国家级)
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const LEVELS = [{ value: '', label: '全部级别' }].concat(C.COMPETITION_LEVEL);
const STATUSES = [
  { value: '', label: '全部状态' },
  { value: 1, label: '报名中' },
  { value: 2, label: '进行中' },
  { value: 3, label: '已结束' }
];
const SORTS = [
  { key: '', label: '综合排序' },
  { key: 'latest', label: '最新发布' },
  { key: 'hot', label: '最热' },
  { key: 'deadline', label: '即将截止' }
];

Page({
  data: {
    levels: LEVELS,
    statuses: STATUSES,
    sorts: SORTS,
    activeLevel: '',
    activeStatus: '',
    activeSort: '',
    levelText: '全部级别',
    statusText: '全部状态',
    keyword: '',

    list: [],
    page: 1,
    hasMore: true,
    loading: true,
    loadStatus: '',
    loaded: false,
    error: false
  },

  onLoad(query) {
    const p = {};
    if (query && query.level !== undefined && query.level !== '') p.activeLevel = Number(query.level);
    if (query && query.keyword) p.keyword = query.keyword;
    this.setData(p);
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
    return api.competition.list({
      level: this.data.activeLevel || undefined,
      status: this.data.activeStatus || undefined,
      sort: this.data.activeSort || undefined,
      keyword: this.data.keyword || undefined
    }, page, 20).then((data) => {
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
  onPickLevel(e) {
    const i = Number(e.detail.value);
    const hit = this.data.levels[i];
    this.setData({ activeLevel: hit.value, levelText: hit.label, page: 1 }, () => this.load(true));
  },

  onPickStatus(e) {
    const i = Number(e.detail.value);
    const hit = this.data.statuses[i];
    this.setData({ activeStatus: hit.value, statusText: hit.label, page: 1 }, () => this.load(true));
  },

  onPickSort(e) {
    const key = e.currentTarget.dataset.key;
    if (key === this.data.activeSort) return;
    this.setData({ activeSort: key, page: 1 }, () => this.load(true));
  },

  onSearchInput(e) { this.setData({ keyword: e.detail.value }); },
  onSearchConfirm() { this.setData({ page: 1 }, () => this.load(true)); },
  onClearKeyword() { this.setData({ keyword: '', page: 1 }, () => this.load(true)); },

  onResetFilter() {
    this.setData({
      activeLevel: '', activeStatus: '', activeSort: '',
      levelText: '全部级别', statusText: '全部状态', keyword: '', page: 1
    }, () => this.load(true));
  },

  onTapItem(e) {
    wx.navigateTo({ url: `/pages/competition/detail?id=${e.currentTarget.dataset.id}` });
  },

  onTapCalendar() { wx.navigateTo({ url: '/pages/competition/calendar' }); },

  onTapPublish() {
    if (!auth.requireAuth({ title: '发布竞赛需要先登录' })) return;
    if (!auth.requireVerified({ title: '发布竞赛' })) return;
    wx.navigateTo({ url: '/pages/competition/publish' });
  },

  onRetry() { this.load(!this.data.list.length); }
});

function decorate(item) {
  const statusMap = { 1: '报名中', 2: '进行中', 3: '已结束', 4: '已取消' };
  const statusTag = { 1: 'success', 2: 'warning', 3: 'gray', 4: 'gray' };
  return Object.assign({}, item, {
    levelText: C.labelOf(C.COMPETITION_LEVEL, item.level, ''),
    statusText: statusMap[item.status] || '报名中',
    statusTag: statusTag[item.status] || 'success',
    startText: util.formatTime(item.startAt || item.startTime, 'date'),
    deadlineText: item.enrollDeadline ? util.formatTime(item.enrollDeadline, 'date') : '',
    cover: item.cover || (Array.isArray(item.images) && item.images[0]) || '/static/placeholder.png',
    enrollText: item.enrolledCount !== undefined ? `${item.enrolledCount} 人报名` : ''
  });
}
