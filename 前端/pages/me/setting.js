/**
 * pages/me/setting —— 设置中心（通用设置 / 帮助与反馈）
 *
 * 来源：
 *   《04》4.4 设置中心
 *   《10》FR-M1-07 退出登录 / FR-M1-08 账号注销（冷静期 15 天）/ FR-M7-06 帮助与反馈
 *   《13》§3.1 POST /users/logout、POST /users/deactivate；§3.7 POST /feedback
 *
 * tab=general（默认）/ tab=help
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const config = require('../../framework/config');

Page({
  data: {
    tab: 'general',
    appVersion: '1.0.0',
    cacheSize: '—',

    feedbackTypes: [
      { value: 1, label: '功能异常' },
      { value: 2, label: '体验建议' },
      { value: 3, label: '内容举报' },
      { value: 4, label: '其他' }
    ],
    fbType: 1,
    fbTypeText: '功能异常',
    fbContent: '',
    fbContact: '',
    submitting: false
  },

  onLoad(query) {
    const tab = (query && query.tab === 'help') ? 'help' : 'general';
    this.setData({ tab });
    this.calcCache();
  },

  onSwitchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ tab });
  },

  calcCache() {
    try {
      const info = wx.getStorageInfoSync();
      this.setData({ cacheSize: (info.currentSize || 0) + ' KB' });
    } catch (e) {
      this.setData({ cacheSize: '—' });
    }
  },

  /* ------------------------------------------------------------------ */
  /* 通用设置                                                            */
  /* ------------------------------------------------------------------ */
  onClearCache() {
    util.confirm('清空缓存不会删除账号数据，仅清理本地临时文件，确定继续？').then((ok) => {
      if (!ok) return;
      const storage = require('../../framework/storage');
      // 保留登录态与用户信息，仅清其余本地缓存
      const tk = storage.token.get();
      const u = storage.user.get();
      wx.clearStorageSync();
      if (tk && tk.accessToken) {
        wx.setStorageSync(config.STORAGE_KEYS.ACCESS_TOKEN, tk.accessToken);
        wx.setStorageSync(config.STORAGE_KEYS.REFRESH_TOKEN, tk.refreshToken);
        wx.setStorageSync(config.STORAGE_KEYS.TOKEN_EXPIRE_AT, tk.expireAt);
      }
      if (u) wx.setStorageSync(config.STORAGE_KEYS.USER_INFO, u);
      this.calcCache();
      util.toast('已清理缓存', 'success');
    });
  },

  onOpenAgreement() {
    wx.navigateTo({
      url: `/pages/webview/index?url=${encodeURIComponent('https://docs.qq.com/campus/user-agreement')}&title=${encodeURIComponent('用户协议')}`
    });
  },

  onOpenPrivacy() {
    wx.navigateTo({
      url: `/pages/webview/index?url=${encodeURIComponent('https://docs.qq.com/campus/privacy')}&title=${encodeURIComponent('隐私政策')}`
    });
  },

  onLogout() {
    util.confirm('确定退出当前账号？', '退出登录', '退出').then((ok) => {
      if (!ok) return;
      api.auth.logout().catch(() => {}).then(() => {
        require('../../framework/storage').token.clear();
        const app = getApp();
        if (app) {
          app.globalData.unreadCount = 0;
          app.setTabBadge(0);
        }
        util.toast('已退出登录', 'success');
        setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 600);
      });
    });
  },

  onDeactivate() {
    if (!auth.requireAuth({ title: '账号注销需要先登录' })) return;
    wx.showModal({
      title: '账号注销',
      content: '注销后进入 15 天冷静期，期间登录可撤销。冷静期结束后账号数据将被永久删除且不可恢复。',
      confirmText: '继续注销',
      confirmColor: '#F04438',
      success: (res) => {
        if (!res.confirm) return;
        wx.showModal({
          title: '再次确认',
          content: '确认提交注销申请？',
          confirmText: '确认注销',
          confirmColor: '#F04438',
          success: (r2) => {
            if (!r2.confirm) return;
            wx.showLoading({ title: '提交中', mask: true });
            api.user.deactivate({ reason: '用户主动注销' }).then(() => {
              wx.hideLoading();
              util.toast('已提交注销申请，进入 15 天冷静期', 'none', 2400);
              setTimeout(() => wx.switchTab({ url: '/pages/home/index' }), 1600);
            }).catch(() => wx.hideLoading());
          }
        });
      }
    });
  },

  /* ------------------------------------------------------------------ */
  /* 帮助与反馈                                                          */
  /* ------------------------------------------------------------------ */
  onFbTypeChange(e) {
    const idx = Number(e.detail.value);
    const hit = this.data.feedbackTypes[idx] || this.data.feedbackTypes[0];
    this.setData({ fbType: hit.value, fbTypeText: hit.label });
  },

  onFbInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  onSubmitFeedback() {
    if (this.data.submitting) return;
    const content = this.data.fbContent.trim();
    if (content.length < 5) {
      util.toast('请至少填写 5 个字的描述');
      return;
    }
    this.setData({ submitting: true });
    api.feedback.create({
      type: this.data.fbType,
      content: content,
      contact: this.data.fbContact.trim()
    }).then(() => {
      this.setData({ submitting: false, fbContent: '', fbContact: '' });
      util.toast('已提交，感谢你的反馈', 'success');
    }).catch(() => this.setData({ submitting: false }));
  },

  onHelpDoc() {
    wx.navigateTo({
      url: `/pages/webview/index?url=${encodeURIComponent('https://docs.qq.com/campus/help')}&title=${encodeURIComponent('使用帮助')}`
    });
  },

  onMyFeedback() {
    api.feedback.list(1, 20).then((data) => {
      const list = (data && data.list) || [];
      if (!list.length) { util.toast('暂无反馈记录'); return; }
      wx.showModal({
        title: '我的反馈',
        content: list.slice(0, 5).map(f => `· ${f.content}`).join('\n'),
        showCancel: false,
        confirmColor: '#1E6FE8'
      });
    }).catch(() => util.toast('暂无法获取反馈记录'));
  },

  onGoReports() {
    wx.navigateTo({ url: '/pages/me/reports' });
  }
});
