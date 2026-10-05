/**
 * pages/message/index —— 消息页（触达与协作）
 *
 * 来源：
 *   《04》一、3 消息：页面顶部（分类/管理）/ 组队协作（申请中心 + 团队会话）/
 *                     内容互动（评论回复/点赞收藏）/ 活动与赛事系统（提醒 + 系统通知）
 *   《C-前端-消息与我的》§2.1 消息中心 / §2.2 申请中心
 *   接口：《13》§3.3 GET /messages?box=&biz=、PUT /messages/read-all、
 *        GET /messages/badge、GET /team-applies?direction=
 *
 * 四段结构：
 *   ① 页面顶部：全部/未读 + 五类入口 + 全部已读 + 设置
 *   ② 组队协作：申请中心入口 + 团队会话（TIM 通道，P1/P2）
 *   ③ 内容互动：评论/回复/提及/点赞/收藏/新增关注（biz=30）
 *   ④ 活动与赛事系统：提醒 + 系统通知（biz=50/40）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const { MESSAGE_BIZ_TABS, BIZ_TYPE, APPLY_STATUS_TEXT, APPLY_STATUS_TAG, contentTypeRoute } = require('../../framework/constants');

const app = getApp();
const PAGE_SIZE = 20;

Page({
  data: {
    /** 顶部：全部 / 未读 */
    box: 'all',
    /** 分类入口（《04》3.1：活动/竞赛/商品/搭子/系统） */
    bizTabs: MESSAGE_BIZ_TABS,
    activeBiz: '',

    /** 未读聚合（GET /messages/badge，驱动 Tab 角标） */
    badge: { total: 0 },

    /** 消息列表 */
    list: [],
    page: 1,
    hasMore: true,
    loading: true,
    loadStatus: '',
    error: false,

    /** 申请中心摘要（组队协作入口） */
    applySummary: null,

    isLogin: false
  },

  onLoad() {
    this.setData({ isLogin: auth.isLogin() });
    this.loadBadge();
    this.loadList(true);
    this.loadApplySummary();
  },

  onShow() {
    const loginNow = auth.isLogin();
    if (loginNow !== this.data.isLogin) {
      this.setData({ isLogin: loginNow });
      this.loadList(true);
    } else {
      this.loadBadge();
    }
    this.loadApplySummary();
  },

  onPullDownRefresh() {
    Promise.all([this.loadList(true), this.loadBadge(), this.loadApplySummary()])
      .then(() => wx.stopPullDownRefresh())
      .catch(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMore();
  },

  /* ------------------------------------------------------------------ */
  /* 列表加载                                                            */
  /* ------------------------------------------------------------------ */
  loadList(reset) {
    if (!auth.isLogin()) {
      this.setData({ loading: false, list: [], hasMore: false });
      return Promise.resolve();
    }
    const page = reset ? 1 : (this.data.page + 1);
    this.setData({
      loading: reset,
      loadStatus: reset ? '' : 'loading',
      error: false
    });

    return api.message.list(this.data.box, this.data.activeBiz, page, PAGE_SIZE)
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
      .catch(() => {
        this.setData({ loading: false, loadStatus: '', error: true });
      });
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loadStatus === 'loading' || this.data.loading) return;
    this.loadList(false);
  },

  onRetry() {
    this.loadList(!this.data.list.length);
  },

  /* ------------------------------------------------------------------ */
  /* 顶部：分类与已读                                                     */
  /* ------------------------------------------------------------------ */
  onSwitchBox(e) {
    const box = e.currentTarget.dataset.box;
    if (box === this.data.box) return;
    this.setData({ box, page: 1 }, () => this.loadList(true));
  },

  onSwitchBiz(e) {
    const biz = e.currentTarget.dataset.biz;
    if (biz === this.data.activeBiz) return;
    this.setData({ activeBiz: biz, page: 1 }, () => this.loadList(true));
  },

  /** 全部已读（FR-M3-02） */
  onReadAll() {
    if (!auth.requireAuth({ title: '该操作需要先登录' })) return;
    if (!this.data.list.length) {
      util.toast('暂无消息');
      return;
    }
    util.confirm('确定将所有消息标记为已读？').then(ok => {
      if (!ok) return;
      api.message.readAll().then(() => {
        const list = this.data.list.map(m => Object.assign({}, m, { isRead: 1 }));
        this.setData({ list });
        this.loadBadge();
        app.refreshBadge();
        util.toast('已全部标记为已读', 'success');
      });
    });
  },

  onTapSetting() {
    wx.navigateTo({ url: '/pages/me/setting-notify' });
  },

  /* ------------------------------------------------------------------ */
  /* 未读角标                                                            */
  /* ------------------------------------------------------------------ */
  loadBadge() {
    if (!auth.isLogin()) {
      this.setData({ badge: { total: 0 } });
      return Promise.resolve();
    }
    return api.message.badge()
      .then(data => {
        const d = data || {};
        this.setData({ badge: d });
        app.globalData.unreadCount = d.total || 0;
        app.setTabBadge(d.total || 0);
      })
      .catch(() => {});
  },

  /* ------------------------------------------------------------------ */
  /* 申请中心摘要（组队协作）                                              */
  /* ------------------------------------------------------------------ */
  loadApplySummary() {
    if (!auth.isLogin()) {
      this.setData({ applySummary: null });
      return Promise.resolve();
    }
    return api.team.applies('received', 1, 5)
      .then(data => {
        const list = normalize(data);
        const pending = list.filter(i => i.status === 0);
        this.setData({ applySummary: { total: list.length, pending: pending.length, top: list.slice(0, 3) } });
      })
      .catch(() => this.setData({ applySummary: null }));
  },

  onTapApplyCenter() {
    if (!auth.requireAuth({ title: '查看申请需要先登录' })) return;
    wx.navigateTo({ url: '/pages/team/apply-center' });
  },

  /* ------------------------------------------------------------------ */
  /* 消息项交互                                                          */
  /* ------------------------------------------------------------------ */
  /** 点击消息：标为已读 + 按 link_type/link_id 路由（《C》§2.1） */
  onTapMessage(e) {
    const item = e.currentTarget.dataset.item || {};
    if (!item.isRead) {
      api.message.read(item.id).catch(() => {});
      const list = this.data.list.map(m => m.id === item.id ? Object.assign({}, m, { isRead: 1 }) : m);
      this.setData({ list });
      this.loadBadge();
      app.refreshBadge();
    }

    const route = contentTypeRoute(item.linkType, item.linkId);
    if (route) {
      wx.navigateTo({ url: route, fail: () => util.toast('目标内容不可用') });
    } else if (item.linkType === 20 || item.bizType === BIZ_TYPE.TEAM) {
      wx.navigateTo({ url: '/pages/team/apply-center' });
    }
  },

  /** 未登录引导 */
  onLogin() {
    auth.requireAuth({ title: '登录后查看消息' });
  },

  /** 私信入口（TIM 通道，P1） */
  onTapChat() {
    if (!auth.requireAuth({ title: '使用私信需要先登录' })) return;
    wx.showModal({
      title: '私信与群聊',
      content: '私信与队伍群聊由腾讯云 IM 提供（详见《13》§3.3 GET /im/sign）。该能力属于 P1/P2 交付批次，接口已按契约预留，待 SDK 接入后开放。',
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#1E6FE8'
    });
  }
});

/* -------------------------------------------------------------------- */
function normalize(data) {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  return data.list || data.records || [];
}

const BIZ_TYPE_TEXT = { 10: '交易', 20: '组队', 30: '互动', 40: '系统', 50: '活动竞赛', 60: '平台' };

/** 补齐展示字段：相对时间 + 业务分类文案 */
function decorate(item) {
  return Object.assign({}, item, {
    createdAtText: util.relativeTime(item.createdAt),
    bizTypeText: BIZ_TYPE_TEXT[item.bizType] || ''
  });
}
