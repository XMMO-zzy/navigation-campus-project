/**
 * pages/feed/index —— 动态页（内容生产与消费）
 *
 * 来源：
 *   《04》一、2 动态：顶部分类页签 / 动态信息流（6 类卡片）/ 发布动态 / 内容治理
 *   《B-前端-动态与发布》§2 动态页实现要点
 *   接口：《13》§3.2 GET /feed?tab=recommend|follow|activity|campus
 *
 * 实现要点（《B》§2）：
 *   - 切 Tab 不重建列表（保位）
 *   - 下拉刷新 + 上拉分页，size=20，去重键 contentType+contentId
 *   - 「不感兴趣」调 PUT /posts/{id}/dislike 并本地移除
 *   - 私密动态在他人视角直接空态
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const position = require('../../framework/position');
const { FEED_TABS, CONTENT_TYPE, contentTypeRoute } = require('../../framework/constants');

const app = getApp();
const PAGE_KEY = 'feed_index';

Page({
  data: {
    tabs: FEED_TABS,
    activeTab: 'recommend',

    /** 每个 Tab 独立的数据（切 Tab 不重建，保留已有内容与位置） */
    feedsMap: {},
    pageMap: {},
    hasMoreMap: {},
    loadingMap: {},
    loadedMap: {},

    /** 当前 Tab 的派生数据（供渲染） */
    list: [],
    loading: true,
    hasMore: true,
    loadStatus: '',
    error: false,

    scrollTop: 0
  },

  onLoad(query) {
    const tab = query && query.tab && FEED_TABS.some(t => t.key === query.tab) ? query.tab : 'recommend';
    this.setData({ activeTab: tab });
    this.loadTab(tab, true);
  },

  onShow() {
    // 发布成功后可通过全局标记触发刷新
    if (app.globalData.needRefreshFeed) {
      app.globalData.needRefreshFeed = false;
      this.loadTab(this.data.activeTab, true);
    }
  },

  onPullDownRefresh() {
    this.loadTab(this.data.activeTab, true).then(() => wx.stopPullDownRefresh())
      .catch(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMore();
  },

  onPageScroll(e) {
    this._scrollTop = e.scrollTop;
  },

  onUnload() {
    // 返回保留列表位置与筛选条件（FR-M7-02）
    position.save(PAGE_KEY, this._scrollTop || 0, { tab: this.data.activeTab });
  },

  /* ------------------------------------------------------------------ */
  /* 页签切换（不重建列表）                                                */
  /* ------------------------------------------------------------------ */
  onSwitchTab(e) {
    const tab = e.currentTarget.dataset.key;
    if (tab === this.data.activeTab) return;

    this.setData({ activeTab: tab }, () => this.syncDerived());

    // 首次进入该 Tab 才请求
    if (!this.data.loadedMap[tab]) {
      this.loadTab(tab, true);
    }
  },

  /** 把当前 Tab 的数据映射到渲染字段 */
  syncDerived() {
    const tab = this.data.activeTab;
    this.setData({
      list: this.data.feedsMap[tab] || [],
      loading: !!this.data.loadingMap[tab] && !(this.data.feedsMap[tab] || []).length,
      hasMore: this.data.hasMoreMap[tab] !== false,
      loadStatus: this.data.loadStatusMap ? (this.data.loadStatusMap[tab] || '') : '',
      error: !!this.data.errorMap ? !!this.data.errorMap[tab] : false
    });
  },

  /* ------------------------------------------------------------------ */
  /* 数据加载                                                            */
  /* ------------------------------------------------------------------ */
  loadTab(tab, reset) {
    const page = reset ? 1 : (this.data.pageMap[tab] || 1) + 1;

    const loadingMap = Object.assign({}, this.data.loadingMap, { [tab]: true });
    const errorMap = Object.assign({}, this.data.errorMap || {}, { [tab]: false });
    const statusMap = Object.assign({}, this.data.loadStatusMap || {}, { [tab]: reset ? '' : 'loading' });
    this.setData({ loadingMap, errorMap, loadStatusMap: statusMap }, () => this.syncDerived());

    if (!reset && !auth.isLogin() && tab === 'follow') {
      // 关注页签需登录（BS-01）
      const feedsMap = Object.assign({}, this.data.feedsMap, { [tab]: [] });
      const loadedMap = Object.assign({}, this.data.loadedMap, { [tab]: true });
      this.setData({ feedsMap, loadedMap }, () => this.syncDerived());
      return Promise.resolve();
    }

    return api.feed.list(tab, page, 20)
      .then(data => {
        const incoming = normalize(data);
        const prev = reset ? [] : (this.data.feedsMap[tab] || []);
        // 去重键 contentType+contentId（《B》§2 TC-M2-07）
        const merged = dedupe(prev.concat(incoming));
        const hasMore = incoming.length >= 20;

        const feedsMap = Object.assign({}, this.data.feedsMap, { [tab]: merged });
        const pageMap = Object.assign({}, this.data.pageMap, { [tab]: page });
        const hasMoreMap = Object.assign({}, this.data.hasMoreMap, { [tab]: hasMore });
        const loadingMap2 = Object.assign({}, this.data.loadingMap, { [tab]: false });
        const loadedMap = Object.assign({}, this.data.loadedMap, { [tab]: true });
        const statusMap2 = Object.assign({}, this.data.loadStatusMap || {}, { [tab]: hasMore ? '' : 'nomore' });

        this.setData({
          feedsMap, pageMap, hasMoreMap, loadingMap: loadingMap2, loadedMap, loadStatusMap: statusMap2
        }, () => this.syncDerived());
      })
      .catch(() => {
        const loadingMap2 = Object.assign({}, this.data.loadingMap, { [tab]: false });
        const errorMap2 = Object.assign({}, this.data.errorMap || {}, { [tab]: true });
        this.setData({ loadingMap: loadingMap2, errorMap: errorMap2 }, () => this.syncDerived());
      });
  },

  loadMore() {
    const tab = this.data.activeTab;
    if (this.data.hasMoreMap[tab] === false) return;
    if ((this.data.loadStatusMap || {})[tab] === 'loading') return;
    if (this.data.loadingMap[tab]) return;
    this.loadTab(tab, false);
  },

  onRetry() {
    this.loadTab(this.data.activeTab, !(this.data.feedsMap[this.data.activeTab] || []).length);
  },

  /* ------------------------------------------------------------------ */
  /* 交互                                                                */
  /* ------------------------------------------------------------------ */
  onTapCard(e) {
    const item = e.detail.item;
    const route = contentTypeRoute(item.contentType, item.contentId);
    if (route) wx.navigateTo({ url: route, fail: () => util.toast('内容暂不可用') });
  },

  onCardAction(e) {
    const { action, item } = e.detail;
    if (!auth.requireAuth({ title: '该操作需要先登录' })) return;

    switch (action) {
      case 'like': this.toggleLike(item); break;
      case 'comment':
        wx.navigateTo({ url: `/pages/post/detail?id=${item.contentId}&focus=comment` });
        break;
      case 'favorite': this.toggleFavorite(item); break;
      case 'share': util.toast('请使用右上角「···」分享给好友'); break;
      case 'dislike': this.dislike(item); break;
      case 'report':
        wx.navigateTo({
          url: `/pages/me/reports?action=create&targetType=${item.contentType}&targetId=${item.contentId}`
        });
        break;
      default: break;
    }
  },

  toggleLike(item) {
    if (item.contentType !== CONTENT_TYPE.POST) {
      util.toast('该内容暂不支持点赞');
      return;
    }
    const liked = !!item.liked;
    this.patchItem(item, {
      liked: !liked,
      'cnt.like': Math.max(0, ((item.cnt && item.cnt.like) || 0) + (liked ? -1 : 1))
    });
    const req = liked ? api.post.unlike(item.contentId) : api.post.like(item.contentId);
    req.catch(() => this.patchItem(item, {
      liked: liked,
      'cnt.like': (item.cnt && item.cnt.like) || 0
    }));
  },

  toggleFavorite(item) {
    const faved = !!item.favorited;
    let req;
    switch (item.contentType) {
      case CONTENT_TYPE.POST:
        req = faved ? api.post.unfavorite(item.contentId) : api.post.favorite(item.contentId); break;
      case CONTENT_TYPE.GOODS:
        req = faved ? api.goods.unfavorite(item.contentId) : api.goods.favorite(item.contentId); break;
      case CONTENT_TYPE.BUDDY:
        req = faved ? api.buddy.unfavorite(item.contentId) : api.buddy.favorite(item.contentId); break;
      default:
        util.toast('该内容暂不支持收藏');
        return;
    }
    this.patchItem(item, {
      favorited: !faved,
      'cnt.favorite': Math.max(0, ((item.cnt && item.cnt.favorite) || 0) + (faved ? -1 : 1))
    });
    req.catch(() => this.patchItem(item, {
      favorited: faved,
      'cnt.favorite': (item.cnt && item.cnt.favorite) || 0
    }));
  },

  /** 定位当前 Tab 列表中的某项并局部更新 */
  patchItem(item, patch) {
    const tab = this.data.activeTab;
    const arr = (this.data.feedsMap[tab] || []).slice();
    const idx = arr.findIndex(f => f.contentType === item.contentType && f.contentId === item.contentId);
    if (idx < 0) return;

    const target = Object.assign({}, arr[idx]);
    Object.keys(patch).forEach(k => {
      if (k.indexOf('.') > -1) {
        const [p, c] = k.split('.');
        target[p] = Object.assign({}, target[p], { [c]: patch[k] });
      } else {
        target[k] = patch[k];
      }
    });
    arr[idx] = target;

    const feedsMap = Object.assign({}, this.data.feedsMap, { [tab]: arr });
    this.setData({ feedsMap }, () => this.syncDerived());
  },

  /** 不感兴趣（FR-M2-11） */
  dislike(item) {
    if (item.contentType === CONTENT_TYPE.POST) {
      api.post.dislike(item.contentId).catch(() => {});
    }
    const tab = this.data.activeTab;
    const arr = (this.data.feedsMap[tab] || []).filter(
      f => !(f.contentType === item.contentType && f.contentId === item.contentId)
    );
    const feedsMap = Object.assign({}, this.data.feedsMap, { [tab]: arr });
    this.setData({ feedsMap }, () => this.syncDerived());
    util.toast('已减少类似内容推荐');
  },

  /* ------------------------------------------------------------------ */
  /* 发布入口（FR-M2-04）                                                 */
  /* ------------------------------------------------------------------ */
  onTapPublish() {
    if (!auth.requireAuth({ title: '发布内容需要先登录' })) return;
    wx.navigateTo({ url: '/pages/publish/index' });
  },

  /** 关注页签未登录 → 去登录 */
  onFollowLogin() {
    auth.requireAuth({ title: '登录后查看关注内容' });
  }
});

/* -------------------------------------------------------------------- */
/* 工具                                                                  */
/* -------------------------------------------------------------------- */
function normalize(data) {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  return data.list || data.records || [];
}

function dedupe(list) {
  const seen = {};
  const out = [];
  list.forEach(item => {
    const key = `${item.contentType}_${item.contentId}`;
    if (seen[key]) return;
    seen[key] = 1;
    out.push(item);
  });
  return out;
}
