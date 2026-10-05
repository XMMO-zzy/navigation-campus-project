/**
 * pages/me/index —— 我的（资产与身份）
 *
 * 来源：
 *   《04》一、4 我的：个人主页头部 / 我的商品·活动·竞赛·搭子 / 我的管理 / 设置中心
 *   《C-前端-消息与我的》§2.4 我的页
 *   接口：《13》§3.1 GET /users/me、§3.4 GET /goods/my、§3.5 my-enrolls、§3.6 buddy-posts/my
 *
 * 结构：
 *   ① 个人主页头部：资料卡（基础/能力/数据概览）+ 右上角设置（含安全风险标记）
 *   ② 记录区：商品订单 / 活动记录 / 竞赛参赛记录与成果
 *   ③ 管理区：组队管理 / 内容管理 / 认证
 *   ④ 设置中心入口
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const { VERIFY_STATUS } = auth;

const VERIFY_TEXT = {
  0: '未认证',
  1: '认证中',
  2: '已认证',
  3: '未通过'
};

/** 我的管理入口（《04》4.3 我的管理） */
const MANAGE_ENTRIES = [
  { key: 'team', label: '组队管理', icon: '👥', url: '/pages/team/apply-center' },
  { key: 'content', label: '内容管理', icon: '📄', url: '/pages/me/records?tab=content' },
  { key: 'verify', label: '身份认证', icon: '🎓', url: '/pages/auth/verify' }
];

/** 设置中心入口（《04》4.4 设置中心） */
const SETTING_ENTRIES = [
  { key: 'account', label: '账号管理', icon: '👤', url: '/pages/me/setting-account' },
  { key: 'notify', label: '消息通知', icon: '🔔', url: '/pages/me/setting-notify' },
  { key: 'privacy', label: '隐私设置', icon: '🔒', url: '/pages/me/setting-privacy' },
  { key: 'general', label: '通用设置', icon: '⚙️', url: '/pages/me/setting?tab=general' },
  { key: 'help', label: '帮助与反馈', icon: '💡', url: '/pages/me/setting?tab=help' }
];

Page({
  data: {
    isLogin: false,
    loading: true,

    /** 用户资料（GET /users/me） */
    user: null,
    verifyText: '未认证',
    verifyStatus: 0,
    roleText: '游客',

    /** 数据概览（《04》4.1 个人资料卡·数据概览） */
    stats: { activityCount: 0, competitionCount: 0, postCount: 0 },

    /** 记录区摘要 */
    orderCount: 0,
    enrollCount: 0,

    manageEntries: MANAGE_ENTRIES,
    settingEntries: SETTING_ENTRIES,

    /** 安全风险标记（《04》4.1 右上角设置图标·状态提示） */
    hasSecurityRisk: false
  },

  onShow() {
    this.setData({ isLogin: auth.isLogin() });
    if (auth.isLogin()) this.loadProfile();
    else this.setData({ loading: false, user: null });
  },

  onPullDownRefresh() {
    const p = auth.isLogin() ? this.loadProfile() : Promise.resolve();
    p.then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  /* ------------------------------------------------------------------ */
  /* ① 个人主页头部                                                       */
  /* ------------------------------------------------------------------ */
  loadProfile() {
    this.setData({ loading: true });
    return api.user.me()
      .then(user => {
        const u = user || {};
        require('../../framework/storage').user.save(u);
        this.setData({
          loading: false,
          user: u,
          verifyStatus: u.verifyStatus || 0,
          verifyText: VERIFY_TEXT[u.verifyStatus || 0],
          roleText: u.userType === 2 ? '认证用户' : '普通用户',
          stats: {
            activityCount: u.activityCount || (u.stats && u.stats.activityCount) || 0,
            competitionCount: u.competitionCount || (u.stats && u.stats.competitionCount) || 0,
            postCount: u.postCount || (u.stats && u.stats.postCount) || 0
          },
          hasSecurityRisk: !!u.hasSecurityRisk
        });
        // 同步全局角色态
        const app = getApp();
        if (app) app.globalData.userType = u.userType;
      })
      .catch(() => this.setData({ loading: false }));
  },

  onTapLogin() {
    auth.requireAuth({ title: '登录后查看个人主页' });
  },

  onTapEditProfile() {
    if (!auth.requireAuth({ title: '登录后编辑资料' })) return;
    if (this.data.user) {
      wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${this.data.user.id}` });
    } else {
      wx.navigateTo({ url: '/pages/me/profile-edit' });
    }
  },

  onTapSetting() {
    wx.navigateTo({ url: '/pages/me/setting' });
  },

  /** 认证状态点击 → 认证页（四态，FR-M1-05/06） */
  onTapVerify() {
    if (!auth.requireAuth({ title: '登录后完成学号认证' })) return;
    wx.navigateTo({ url: '/pages/auth/verify' });
  },

  /* ------------------------------------------------------------------ */
  /* ② 记录区                                                           */
  /* ------------------------------------------------------------------ */
  onTapOrders(e) {
    if (!auth.requireAuth({ title: '登录后查看订单' })) return;
    const role = e.currentTarget.dataset.role || 'buy';
    wx.navigateTo({ url: `/pages/order/list?role=${role}` });
  },

  onTapMyGoods() {
    if (!auth.requireAuth({ title: '登录后查看我的商品' })) return;
    wx.navigateTo({ url: '/pages/goods/list?view=published' });
  },

  onTapMyBuddy() {
    if (!auth.requireAuth({ title: '登录后查看我的搭子' })) return;
    wx.navigateTo({ url: '/pages/buddy/list?view=published' });
  },

  onTapMyActivity() {
    if (!auth.requireAuth({ title: '登录后查看活动记录' })) return;
    wx.navigateTo({ url: '/pages/me/records?tab=activity' });
  },

  onTapMyCompetition() {
    if (!auth.requireAuth({ title: '登录后查看参赛记录' })) return;
    wx.navigateTo({ url: '/pages/me/records?tab=competition' });
  },

  /* ------------------------------------------------------------------ */
  /* ③ 管理区 / ④ 设置中心                                                */
  /* ------------------------------------------------------------------ */
  onTapEntry(e) {
    const url = e.currentTarget.dataset.url;
    if (!url) return;
    if (!auth.requireAuth({ title: '该功能需要先登录' })) return;
    wx.navigateTo({ url, fail: () => this.setData({}) });
  },

  onTapReports() {
    if (!auth.requireAuth({ title: '登录后查看举报记录' })) return;
    wx.navigateTo({ url: '/pages/me/reports' });
  }
});
