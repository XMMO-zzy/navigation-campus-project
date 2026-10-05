/**
 * pages/activity/list —— 校园活动列表
 *
 * 来源：
 *   《10》FR-M5-02 活动列表 / FR-M5-04 多维筛选
 *   《13》§3.5 GET /activities?category=&status=&keyword=
 *   《12》§2.5 activity.category(1 校园活动/2 学术讲座/3 学术调研)
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const CATEGORIES = [{ value: '', label: '全部' }].concat(C.ACTIVITY_CATEGORY);
const SORTS = [
  { key: '', label: '综合排序' },
  { key: 'latest', label: '最新发布' },
  { key: 'hot', label: '最热报名' },
  { key: 'deadline', label: '即将截止' }
];

Page({
  data: {
    categories: CATEGORIES,
    sorts: SORTS,
    activeCategory: '',
    activeSort: '',
    keyword: '',
    sortText: '综合排序',

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
    if (query && query.category !== undefined && query.category !== '') {
      p.activeCategory = Number(query.category);
    }
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
    return api.activity.list({
      category: this.data.activeCategory || undefined,
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
  onPickCategory(e) {
    const value = e.currentTarget.dataset.value;
    if (value === this.data.activeCategory) return;
    this.setData({ activeCategory: value, page: 1 }, () => this.load(true));
  },

  onPickSort(e) {
    const key = e.currentTarget.dataset.key;
    const hit = SORTS.filter(s => s.key === key)[0];
    this.setData({
      activeSort: key,
      sortText: hit ? hit.label : '综合排序',
      page: 1
    }, () => this.load(true));
  },

  onSearchInput(e) { this.setData({ keyword: e.detail.value }); },

  onSearchConfirm() {
    this.setData({ page: 1 }, () => this.load(true));
  },

  onClearKeyword() {
    this.setData({ keyword: '', page: 1 }, () => this.load(true));
  },

  onTapItem(e) {
    wx.navigateTo({ url: `/pages/activity/detail?id=${e.currentTarget.dataset.id}` });
  },

  onTapPublish() {
    if (!auth.requireAuth({ title: '发布活动需要先登录' })) return;
    if (!auth.requireVerified({ title: '发布活动' })) return;
    wx.navigateTo({ url: '/pages/activity/publish' });
  },

  onRetry() { this.load(!this.data.list.length); }
});

function decorate(item) {
  const statusMap = { 0: '待开始', 1: '报名中', 2: '进行中', 3: '已结束', 4: '已取消' };
  const statusTag = { 0: 'info', 1: 'success', 2: 'warning', 3: 'gray', 4: 'gray' };
  return Object.assign({}, item, {
    categoryText: C.labelOf(C.ACTIVITY_CATEGORY, item.category, '活动'),
    statusText: statusMap[item.status] !== undefined ? statusMap[item.status] : '报名中',
    statusTag: statusTag[item.status] || 'info',
    startText: util.formatTime(item.startAt || item.startTime, 'date'),
    enrollText: item.enrolledCount !== undefined
      ? (item.quota ? `${item.enrolledCount}/${item.quota}` : `${item.enrolledCount} 人已报名`)
      : '',
    cover: item.cover || (Array.isArray(item.images) && item.images[0]) || '/static/placeholder.png',
    deadlineText: item.enrollDeadline ? `报名截止 ${util.formatTime(item.enrollDeadline, 'date')}` : ''
  });
}
