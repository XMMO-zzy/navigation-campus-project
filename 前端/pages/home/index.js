/**
 * pages/home/index —— 首页（聚合与分发中枢）
 *
 * 来源：
 *   《04-思维导图（重构版）》一、1 首页：顶部导航 / 快捷服务 / 重要消息 / 内容推荐
 *   《A-前端-首页与全局框架》§2 首页四区块实现清单
 *   接口：《13》§3.2 GET /home/feeds、GET /todos、§3.1 GET /users/me
 *
 * 四区块：
 *   1. 顶部导航：全局搜索入口 + 消息入口（未读角标）
 *   2. 快捷服务宫格：二手 / 搭子 / 活动 / 竞赛
 *   3. 重要消息（GET /todos）：即将截止 / 报名开始 / 规则更新 / 协作待办
 *   4. 内容推荐：机会推荐（GET /home/feeds）+ 信息推荐（官方通知/规则更新/平台公告）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const store = require('../../framework/storage');
const { CONTENT_TYPE, contentTypeRoute } = require('../../framework/constants');

const app = getApp();

/** 快捷服务四宫格（《04》1.2 快捷服务） */
const QUICK_SERVICES = [
  { key: 'goods', label: '二手', desc: '闲置交易', icon: '🛍', color: '#1E6FE8', url: '/pages/goods/list' },
  { key: 'buddy', label: '搭子', desc: '找同好', icon: '🤝', color: '#12B76A', url: '/pages/buddy/list' },
  { key: 'activity', label: '活动', desc: '校园活动', icon: '🎪', color: '#F79009', url: '/pages/activity/list' },
  { key: 'competition', label: '竞赛', desc: '赛事日程', icon: '🏆', color: '#7A5AF8', url: '/pages/competition/list' }
];

Page({
  data: {
    quickServices: QUICK_SERVICES,

    /** 未读消息数（消息入口角标，FR-M3-08） */
    unread: 0,

    /** 重要消息待办（GET /todos）| FR-M3-05 */
    todos: {
      deadline: [],   // 即将截止
      enrollStart: [], // 报名开始
      ruleUpdate: [],  // 规则更新
      collab: []       // 协作待办
    },
    todoLoading: true,
    todoActiveTab: 'deadline',

    /** 内容推荐流（GET /home/feeds） */
    feeds: [],
    page: 1,
    hasMore: true,
    feedLoading: true,
    loadStatus: '',
    feedError: false,

    /** 是否登录 */
    isLogin: false,
    greeting: ''
  },

  onLoad() {
    this.setData({ greeting: this.buildGreeting() });
  },

  onShow() {
    this.setData({
      isLogin: auth.isLogin(),
      unread: app.globalData.unreadCount || 0
    });
    // 每次回到首页刷新待办与未读（《04》重要消息为高频刚需）
    this.loadTodos();
    this.refreshUnread();

    // 登录态变化后重新加载推荐流（个性化）
    if (this._lastLoginState !== auth.isLogin()) {
      this._lastLoginState = auth.isLogin();
      this.reloadFeeds();
    } else if (!this.data.feeds.length) {
      this.reloadFeeds();
    }
  },

  onPullDownRefresh() {
    Promise.all([this.loadTodos(), this.reloadFeeds()])
      .then(() => wx.stopPullDownRefresh())
      .catch(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMoreFeeds();
  },

  /* ------------------------------------------------------------------ */
  /* 顶部导航                                                            */
  /* ------------------------------------------------------------------ */
  buildGreeting() {
    const h = new Date().getHours();
    if (h < 6) return '夜深了';
    if (h < 11) return '早上好';
    if (h < 14) return '中午好';
    if (h < 18) return '下午好';
    return '晚上好';
  },

  /** 搜索入口（FR-M2-07） */
  onTapSearch() {
    wx.navigateTo({ url: '/pages/search/index' });
  },

  /** 消息入口：跳转消息 Tab 并定位未读（《04》1.1 消息入口·点击进入） */
  onTapMessage() {
    wx.switchTab({ url: '/pages/message/index' });
  },

  /** 刷新未读角标（FR-M3-08） */
  refreshUnread() {
    if (!auth.isLogin()) {
      this.setData({ unread: 0 });
      return;
    }
    app.refreshBadge()
      .then(n => this.setData({ unread: n || 0 }))
      .catch(() => {});
  },

  /* ------------------------------------------------------------------ */
  /* 快捷服务                                                            */
  /* ------------------------------------------------------------------ */
  onTapQuick(e) {
    const url = e.currentTarget.dataset.url;
    if (!url) return;
    wx.navigateTo({ url, fail: () => wx.switchTab({ url }) });
  },

  /* ------------------------------------------------------------------ */
  /* 重要消息（待办）FR-M3-05                                            */
  /* ------------------------------------------------------------------ */
  loadTodos() {
    // ⚠️ /todos 是登录接口（后端 @RequireRole(USER)）：
    // 游客态必须跳过，否则返回 40001 会被 request 层自动拉起登录页，
    // 导致「一进首页就弹登录」——破坏「不登录也能逛」（《10》FR-M1-03 /《17》§1）。
    if (!auth.isLogin()) {
      this.setData({
        todoLoading: false,
        todos: { deadline: [], enrollStart: [], ruleUpdate: [], collab: [] }
      });
      return Promise.resolve();
    }
    return api.feed.todos()
      .then(data => {
        const d = data || {};
        this.setData({
          todoLoading: false,
          todos: {
            deadline: d.deadline || d.deadlines || [],
            enrollStart: d.enrollStart || [],
            ruleUpdate: d.ruleUpdate || [],
            collab: d.collab || d.collaboration || []
          }
        });
      })
      .catch(() => {
        this.setData({ todoLoading: false });
      });
  },

  onSwitchTodoTab(e) {
    this.setData({ todoActiveTab: e.currentTarget.dataset.key });
  },

  /** 点击待办项 → 跳转对应业务详情 */
  onTapTodo(e) {
    const item = e.currentTarget.dataset.item || {};
    // 协作待办：需登录（BS-01）
    if (this.data.todoActiveTab === 'collab' && !auth.requireAuth({ title: '查看协作待办需要登录' })) return;

    const route = contentTypeRoute(item.targetType || item.contentType, item.targetId || item.contentId);
    if (route) {
      wx.navigateTo({ url: route, fail: () => util.toast('内容暂不可用') });
    } else {
      util.toast('该待办暂无详情页');
    }
  },

  onTapTodoMore() {
    if (!auth.requireAuth({ title: '查看全部待办需要登录' })) return;
    wx.switchTab({ url: '/pages/message/index' });
  },

  /* ------------------------------------------------------------------ */
  /* 内容推荐流（GET /home/feeds）                                        */
  /* ------------------------------------------------------------------ */
  reloadFeeds() {
    this.setData({ page: 1, hasMore: true, feedLoading: true, feedError: false, loadStatus: '' });
    return api.feed.home(1, 20)
      .then(data => {
        const list = this.normalizeList(data);
        this.setData({
          feeds: list,
          feedLoading: false,
          hasMore: list.length >= 20,
          loadStatus: list.length >= 20 ? '' : 'nomore'
        });
      })
      .catch(() => {
        this.setData({ feedLoading: false, feedError: true, loadStatus: '' });
      });
  },

  loadMoreFeeds() {
    if (!this.data.hasMore || this.data.loadStatus === 'loading') return;
    const next = this.data.page + 1;
    this.setData({ loadStatus: 'loading' });
    api.feed.home(next, 20)
      .then(data => {
        const list = this.normalizeList(data);
        const merged = this.data.feeds.concat(list);
        this.setData({
          feeds: merged,
          page: next,
          hasMore: list.length >= 20,
          loadStatus: list.length >= 20 ? '' : 'nomore'
        });
      })
      .catch(() => this.setData({ loadStatus: 'error' }));
  },

  /** 统一兼容 {list,total} 与裸数组两种返回 */
  normalizeList(data) {
    if (!data) return [];
    if (Array.isArray(data)) return data;
    return data.list || data.records || [];
  },

  onRetryFeed() {
    this.loadMoreFeeds();
  },

  /** 推荐流空态 → 去逛逛 */
  onFeedEmptyAction() {
    wx.switchTab({ url: '/pages/feed/index' });
  },

  /* ------------------------------------------------------------------ */
  /* 卡片交互                                                            */
  /* ------------------------------------------------------------------ */
  onTapCard(e) {
    const item = e.detail.item;
    const route = contentTypeRoute(item.contentType, item.contentId);
    if (route) wx.navigateTo({ url: route, fail: () => util.toast('内容暂不可用') });
  },

  /**
   * 卡片操作：点赞 / 评论 / 收藏 / 分享 / 不感兴趣 / 举报
   * 全部为写操作 → 走登录守卫（BS-01：游客写操作 40001 + 前端弹登录引导）
   */
  onCardAction(e) {
    const { action, item } = e.detail;
    if (!auth.requireAuth({ title: '该操作需要先登录' })) return;

    switch (action) {
      case 'like':
        this.toggleLike(item);
        break;
      case 'comment':
        wx.navigateTo({ url: `/pages/post/detail?id=${item.contentId}&focus=comment` });
        break;
      case 'favorite':
        this.toggleFavorite(item);
        break;
      case 'share':
        util.toast('请使用右上角「···」分享给好友');
        break;
      case 'dislike':
        this.dislike(item);
        break;
      case 'report':
        this.report(item);
        break;
      default:
        break;
    }
  },

  /** 点赞（乐观更新 + 失败回滚） */
  toggleLike(item) {
    const liked = !!item.liked;
    const idx = this.data.feeds.findIndex(f => f.contentType === item.contentType && f.contentId === item.contentId);
    if (idx < 0) return;

    const patch = {};
    patch[`feeds[${idx}].liked`] = !liked;
    patch[`feeds[${idx}].cnt.like`] = Math.max(0, ((item.cnt && item.cnt.like) || 0) + (liked ? -1 : 1));
    this.setData(patch);

    // 目前仅动态支持点赞接口（《13》§3.2 POST /posts/{id}/like）
    if (item.contentType !== CONTENT_TYPE.POST) {
      util.toast('该内容暂不支持点赞');
      return;
    }
    const req = liked ? api.post.unlike(item.contentId) : api.post.like(item.contentId);
    req.catch(() => {
      const rollback = {};
      rollback[`feeds[${idx}].liked`] = liked;
      rollback[`feeds[${idx}].cnt.like`] = (item.cnt && item.cnt.like) || 0;
      this.setData(rollback);
    });
  },

  /** 收藏（乐观更新 + 失败回滚） */
  toggleFavorite(item) {
    const faved = !!item.favorited;
    const idx = this.data.feeds.findIndex(f => f.contentType === item.contentType && f.contentId === item.contentId);
    if (idx < 0) return;

    const patch = {};
    patch[`feeds[${idx}].favorited`] = !faved;
    patch[`feeds[${idx}].cnt.favorite`] = Math.max(0, ((item.cnt && item.cnt.favorite) || 0) + (faved ? -1 : 1));
    this.setData(patch);

    let req;
    switch (item.contentType) {
      case CONTENT_TYPE.POST:
        req = faved ? api.post.unfavorite(item.contentId) : api.post.favorite(item.contentId);
        break;
      case CONTENT_TYPE.GOODS:
        req = faved ? api.goods.unfavorite(item.contentId) : api.goods.favorite(item.contentId);
        break;
      case CONTENT_TYPE.BUDDY:
        req = faved ? api.buddy.unfavorite(item.contentId) : api.buddy.favorite(item.contentId);
        break;
      default:
        util.toast('该内容暂不支持收藏');
        return;
    }
    req.catch(() => {
      const rollback = {};
      rollback[`feeds[${idx}].favorited`] = faved;
      rollback[`feeds[${idx}].cnt.favorite`] = (item.cnt && item.cnt.favorite) || 0;
      this.setData(rollback);
    });
  },

  /** 不感兴趣（FR-M2-11：本地移除 + 服务端负反馈） */
  dislike(item) {
    if (item.contentType === CONTENT_TYPE.POST) {
      api.post.dislike(item.contentId).catch(() => {});
    }
    const feeds = this.data.feeds.filter(
      f => !(f.contentType === item.contentType && f.contentId === item.contentId)
    );
    this.setData({ feeds });
    util.toast('已减少类似内容推荐');
  },

  /** 举报（FR-M7-03） */
  report(item) {
    wx.navigateTo({
      url: `/pages/me/reports?action=create&targetType=${item.contentType}&targetId=${item.contentId}`
    });
  }
});
