/**
 * app.js —— 小程序入口
 *
 * 职责：
 *   1. 全局登录态初始化（静默检查 Token，拉取用户信息）
 *   2. 注入请求层的登录引导 / 认证引导回调（框架层不直接依赖页面）
 *   3. 未读角标全局同步（FR-M3-08）
 *   4. 网络状态监听（FR-M7-02 离线提示）
 */

const http = require('./framework/request');
const store = require('./framework/storage');
const api = require('./framework/api');
const auth = require('./framework/auth');
const { CODE } = require('./framework/error-code');

App({
  globalData: {
    /** 系统信息（状态栏高度等，供自定义导航栏使用） */
    systemInfo: null,
    statusBarHeight: 20,
    /** 未读消息数（Tab 角标） */
    unreadCount: 0,
    /** 网络是否在线 */
    online: true,
    /** 配置字典缓存（GET /configs） */
    configs: null,
    /** 是否是首次启动（用于登录引导节奏） */
    launched: false
  },

  onLaunch() {
    this.initSystemInfo();
    this.bindRequestHandlers();
    this.watchNetwork();
    this.restoreSession();
  },

  onShow() {
    // 回到前台时刷新未读角标
    if (auth.isLogin()) this.refreshBadge();
  },

  /* ------------------------------------------------------------------ */
  /* 系统信息                                                            */
  /* ------------------------------------------------------------------ */
  initSystemInfo() {
    try {
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      this.globalData.systemInfo = info;
      this.globalData.statusBarHeight = (info && info.statusBarHeight) || 20;
    } catch (e) {
      this.globalData.statusBarHeight = 20;
    }
  },

  /* ------------------------------------------------------------------ */
  /* 请求层回调注入（避免框架层直接跳页面）                                */
  /* ------------------------------------------------------------------ */
  bindRequestHandlers() {
    // 40001：清除本地态 + 弹登录引导
    http.setLoginHandler(() => {
      store.token.clear();
      const pages = getCurrentPages();
      const cur = pages.length ? pages[pages.length - 1] : null;
      if (cur && cur.route === 'pages/auth/login') return;
      const redirect = cur ? auth.buildRedirect(cur) : '/pages/home/index';
      wx.navigateTo({
        url: `/pages/auth/login?redirect=${encodeURIComponent(redirect)}`
      });
    });

    // 40003：引导去认证页
    http.setVerifyHandler((msg) => {
      wx.showModal({
        title: '需要学号认证',
        content: msg || '该功能需要先完成学号认证',
        confirmText: '去认证',
        confirmColor: '#1E6FE8',
        success(res) {
          if (res.confirm) wx.navigateTo({ url: '/pages/auth/verify' });
        }
      });
    });
  },

  /* ------------------------------------------------------------------ */
  /* 网络监听                                                            */
  /* ------------------------------------------------------------------ */
  watchNetwork() {
    wx.getNetworkType({
      success: res => {
        this.globalData.online = res.networkType !== 'none';
      }
    });
    wx.onNetworkStatusChange(res => {
      const wasOffline = !this.globalData.online;
      this.globalData.online = res.isConnected;
      if (wasOffline && res.isConnected) {
        wx.showToast({ title: '网络已恢复', icon: 'none' });
      }
    });
  },

  /* ------------------------------------------------------------------ */
  /* 会话恢复                                                            */
  /* ------------------------------------------------------------------ */
  restoreSession() {
    const { accessToken } = store.token.get();
    if (!accessToken) {
      // 游客态：仅预取配置字典，不触发登录
      this.loadConfigs();
      return;
    }
    // 已有 Token：校验有效性并刷新用户信息
    api.user.me()
      .then(user => {
        store.user.save(user);
        this.refreshBadge();
      })
      .catch(err => {
        if (err && (err.code === CODE.NOT_LOGIN || err.code === CODE.TOKEN_EXPIRED)) {
          store.token.clear();
        }
      })
      .then(() => this.loadConfigs());
  },

  /** 预取前端字典（品类/成色/话题等，无登录也可） */
  loadConfigs() {
    api.config.all()
      .then(data => {
        if (data) {
          this.globalData.configs = data;
          store.set('campus_configs', data);
        }
      })
      .catch(() => { /* 静默失败，使用前端兜底字典 */ });
  },

  /* ------------------------------------------------------------------ */
  /* 未读角标（FR-M3-08：底部 Tab 角标 + 会话角标）                       */
  /* ------------------------------------------------------------------ */
  refreshBadge() {
    if (!auth.isLogin()) {
      this.setTabBadge(0);
      return Promise.resolve(0);
    }
    return api.message.badge()
      .then(data => {
        const total = (data && (data.total !== undefined ? data.total : data.count)) || 0;
        this.globalData.unreadCount = total;
        this.setTabBadge(total);
        return total;
      })
      .catch(() => 0);
  },

  setTabBadge(count) {
    const n = Number(count) || 0;
    // Tab index 2 = 消息
    if (n > 0) {
      wx.setTabBarBadge({
        index: 2,
        text: n > 99 ? '99+' : String(n),
        fail: () => {}
      });
    } else {
      wx.removeTabBarBadge({ index: 2, fail: () => {} });
    }
  }
});
