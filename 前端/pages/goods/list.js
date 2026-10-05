/**
 * pages/goods/list —— 二手商品列表（两栏瀑布流 + 筛选 + 我的视图）
 *
 * 来源：
 *   《04》一、1.2 快捷服务·二手服务区：二手商品 / 我的商品 / 我的关注 / 我的订单
 *   《10》FR-M4-02 商品浏览：两栏瀑布流，按品类/成色/价格区间筛选
 *   《13》§3.4 GET /goods?category=&cond=&min=&max=、GET /goods/my?view=
 *
 * view 参数：''（全部在售）| published（我发布的）| favorite（我关注的）| orders（我的订单）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const position = require('../../framework/position');
const { GOODS_CATEGORY, GOODS_COND, labelOf } = require('../../framework/constants');

const PAGE_KEY = 'goods_list';
const PAGE_SIZE = 20;

Page({
  data: {
    /** 视图模式（《04》二手服务区四入口） */
    view: '',
    viewTabs: [
      { key: '', label: '全部商品' },
      { key: 'published', label: '我的商品' },
      { key: 'favorite', label: '我的关注' },
      { key: 'orders', label: '我的订单' }
    ],

    /** 筛选条件（FR-M4-02） */
    categories: GOODS_CATEGORY,
    conds: GOODS_COND,
    filters: { category: '', cond: '', min: '', max: '' },
    filterVisible: false,

    list: [],
    page: 1,
    hasMore: true,
    loading: true,
    loadStatus: '',
    error: false,

    isLogin: false
  },

  onLoad(query) {
    const view = (query && ['published', 'favorite', 'orders'].indexOf(query.view) > -1) ? query.view : '';
    const restored = position.restore(PAGE_KEY);

    this.setData({
      view,
      isLogin: auth.isLogin(),
      filters: (restored && restored.filters) || this.data.filters
    });
    this._scrollTop = restored ? restored.scrollTop : 0;
    this.load(true);
  },

  onShow() {
    this.setData({ isLogin: auth.isLogin() });
    if (this._needRefresh) {
      this._needRefresh = false;
      this.load(true);
    }
  },

  onPullDownRefresh() {
    this.load(true).then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  onReachBottom() { this.loadMore(); },

  onPageScroll(e) { this._scrollTop = e.scrollTop; },

  onUnload() {
    position.save(PAGE_KEY, this._scrollTop || 0, this.data.filters);
  },

  /* ------------------------------------------------------------------ */
  /* 数据加载                                                            */
  /* ------------------------------------------------------------------ */
  load(reset) {
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: reset, loadStatus: reset ? '' : 'loading', error: false });

    // 我的视图需登录（BS-01）
    if (this.data.view && !auth.isLogin()) {
      this.setData({ loading: false, list: [], hasMore: false });
      return Promise.resolve();
    }

    const f = this.data.filters;
    const req = this.data.view
      ? api.goods.my(this.data.view, page, PAGE_SIZE)
      : api.goods.list({ category: f.category, cond: f.cond, min: f.min, max: f.max }, page, PAGE_SIZE);
    return req
      .then(data => {
        const incoming = normalize(data).map(decorate);
        const merged = reset ? incoming : this.data.list.concat(incoming);
        this.setData({
          list: merged,
          page,
          hasMore: incoming.length >= PAGE_SIZE,
          loading: false,
          loadStatus: incoming.length >= PAGE_SIZE ? '' : 'nomore'
        });
      })
      .catch(() => this.setData({ loading: false, loadStatus: '', error: true }));
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loadStatus === 'loading' || this.data.loading) return;
    this.load(false);
  },

  onRetry() { this.load(!this.data.list.length); },

  /* ------------------------------------------------------------------ */
  /* 视图与筛选                                                          */
  /* ------------------------------------------------------------------ */
  onSwitchView(e) {
    const view = e.currentTarget.dataset.key;
    if (view === this.data.view) return;
    // 「我的订单」不属于商品视图，跳转订单页（《04》1.2 二手服务区四入口）
    if (view === 'orders') {
      this.onTapOrders();
      return;
    }
    if (view && !auth.requireAuth({ title: '查看我的商品需要登录' })) return;
    this.setData({ view, page: 1 }, () => this.load(true));
  },

  onToggleFilter() {
    this.setData({ filterVisible: !this.data.filterVisible });
  },

  onPickCategory(e) {
    const v = e.currentTarget.dataset.value;
    this.setData({ 'filters.category': this.data.filters.category === v ? '' : v });
  },

  onPickCond(e) {
    const v = e.currentTarget.dataset.value;
    this.setData({ 'filters.cond': this.data.filters.cond === v ? '' : v });
  },

  onPriceInput(e) {
    this.setData({ [`filters.${e.currentTarget.dataset.field}`]: e.detail.value });
  },

  onApplyFilter() {
    this.setData({ filterVisible: false, page: 1 }, () => this.load(true));
  },

  onResetFilter() {
    this.setData({ filters: { category: '', cond: '', min: '', max: '' }, filterVisible: false, page: 1 },
      () => this.load(true));
  },

  /* ------------------------------------------------------------------ */
  /* 导航                                                                */
  /* ------------------------------------------------------------------ */
  onTapItem(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/goods/detail?id=${id}` });
  },

  onTapPublish() {
    if (!auth.requireAuth({ title: '发布商品需要先登录' })) return;
    wx.navigateTo({ url: '/pages/goods/publish' });
  },

  onTapOrders() {
    if (!auth.requireAuth({ title: '查看订单需要登录' })) return;
    wx.navigateTo({ url: '/pages/order/list?role=buy' });
  },

  onLogin() {
    auth.requireAuth({ title: '登录后查看我的商品' });
  }
});

/* -------------------------------------------------------------------- */
function normalize(data) {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  return data.list || data.records || [];
}

function decorate(item) {
  return Object.assign({}, item, {
    priceText: util.formatPrice(item.price),
    origPriceText: item.origPrice ? util.formatPrice(item.origPrice) : '',
    condText: labelOf(GOODS_COND, item.cond, ''),
    categoryText: labelOf(GOODS_CATEGORY, item.categoryId, ''),
    timeText: util.relativeTime(item.createdAt),
    image: item.image || (Array.isArray(item.images) && item.images[0]) || ''
  });
}
