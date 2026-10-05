/**
 * pages/auth/login —— 登录页 / 登录引导浮层（FR-M1-01/02/03）
 *
 * 来源：
 *   《13》§3.1 POST /auth/wx-login、POST /auth/phone-login
 *   《04》一、1.1 顶部导航 /《A》§3.2 权限前端层（登录成功返回原页原位，TC-M1-02）
 *   《10》FR-M1-03 游客模式：仅浏览，写操作触发登录引导
 *   《17》用户使用手册 §1：不登录也能逛（游客模式）
 *
 * 流程：
 *   1. wx.login() 拿 code → POST /auth/wx-login → 存 Token + 用户信息
 *   2. 登录成功 → 若带 redirect 参数则回原页原位（navigateBack + redirect）
 *   3. 「先逛逛」→ 返回上一页保持游客态
 */

const api = require('../../framework/api');
const store = require('../../framework/storage');
const util = require('../../framework/util');
const auth = require('../../framework/auth');

const app = getApp();

Page({
  data: {
    /** 登录引导文案（由调用方传入） */
    title: '登录通航校园',
    desc: '登录后可发布内容、收藏报名、管理你的校园资产',
    redirect: '',

    /** 手机号登录（备用通道，FR-M1-02） */
    showPhoneLogin: false,
    phone: '',
    smsCode: '',
    countdown: 0,

    /** 是否同意协议 */
    agreed: true,

    loading: false
  },

  onLoad(query) {
    this.setData({
      title: (query && query.title) ? decodeURIComponent(query.title) : '登录通航校园',
      redirect: (query && query.redirect) ? decodeURIComponent(query.redirect) : ''
    });
    this._timer = null;
  },

  onUnload() {
    if (this._timer) clearInterval(this._timer);
  },

  /* ------------------------------------------------------------------ */
  /* 协议                                                               */
  /* ------------------------------------------------------------------ */
  onToggleAgree() {
    this.setData({ agreed: !this.data.agreed });
  },

  onOpenAgreement() {
    wx.navigateTo({ url: '/pages/webview/index?type=agreement' });
  },

  onOpenPrivacy() {
    wx.navigateTo({ url: '/pages/webview/index?type=privacy' });
  },

  /* ------------------------------------------------------------------ */
  /* 微信一键授权登录（FR-M1-01）                                          */
  /* ------------------------------------------------------------------ */
  onWxLogin() {
    if (!this.data.agreed) {
      util.toast('请先阅读并同意用户协议与隐私政策');
      return;
    }
    if (this.data.loading) return;
    this.setData({ loading: true });

    wx.login({
      success: res => {
        if (!res.code) {
          this.setData({ loading: false });
          util.toast('微信授权失败，请重试');
          return;
        }
        api.auth.wxLogin(res.code, api.uuid())
          .then(data => this.handleLoginSuccess(data))
          .catch(err => {
            this.setData({ loading: false });
            // 42001 微信授权失败 → 引导重试（《13》§2）
            if (err && err.code === 42001) util.toast('微信服务繁忙，请稍后重试');
          });
      },
      fail: () => {
        this.setData({ loading: false });
        util.toast('微信授权失败，请重试');
      }
    });
  },

  /* ------------------------------------------------------------------ */
  /* 手机号验证码登录（备用通道，FR-M1-02）                                */
  /* ------------------------------------------------------------------ */
  onTogglePhoneLogin() {
    this.setData({ showPhoneLogin: !this.data.showPhoneLogin });
  },

  onPhoneInput(e) {
    this.setData({ phone: e.detail.value });
  },

  onCodeInput(e) {
    this.setData({ smsCode: e.detail.value });
  },

  onSendCode() {
    const phone = this.data.phone.trim();
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      util.toast('请输入正确的手机号');
      return;
    }
    if (this.data.countdown > 0) return;

    // 说明：文档未定义「发送验证码」独立接口；
    // 验证码发送通常由后端在 /auth/phone-login 前置步骤承载，
    // 此处仅做交互与倒计时，待契约补充后接入（不臆造接口）。
    util.toast('验证码已发送（接口待契约补充）');
    this.setData({ countdown: 60 });
    this._timer = setInterval(() => {
      const n = this.data.countdown - 1;
      if (n <= 0) {
        clearInterval(this._timer);
        this._timer = null;
        this.setData({ countdown: 0 });
      } else {
        this.setData({ countdown: n });
      }
    }, 1000);
  },

  onPhoneLogin() {
    if (!this.data.agreed) {
      util.toast('请先阅读并同意用户协议与隐私政策');
      return;
    }
    const { phone, smsCode } = this.data;
    if (!/^1[3-9]\d{9}$/.test(phone.trim())) {
      util.toast('请输入正确的手机号');
      return;
    }
    if (!smsCode.trim()) {
      util.toast('请输入验证码');
      return;
    }
    if (this.data.loading) return;
    this.setData({ loading: true });

    api.auth.phoneLogin(phone.trim(), smsCode.trim(), api.uuid())
      .then(data => this.handleLoginSuccess(data))
      .catch(() => this.setData({ loading: false }));
  },

  /* ------------------------------------------------------------------ */
  /* 登录成功处理                                                        */
  /* ------------------------------------------------------------------ */
  handleLoginSuccess(data) {
    const d = data || {};
    store.token.save(d);
    if (d.user) store.user.save(d.user);

    this.setData({ loading: false });

    // 通知全局刷新未读与用户态
    if (app) {
      app.refreshBadge();
      app.loadConfigs();
    }

    util.toast('登录成功', 'success');

    const redirect = this.data.redirect;
    setTimeout(() => {
      if (redirect) {
        // 返回原页原位（TC-M1-02）
        const pages = getCurrentPages();
        if (pages.length > 1) {
          wx.navigateBack({
            fail: () => wx.reLaunch({ url: redirect })
          });
        } else {
          wx.reLaunch({ url: redirect, fail: () => wx.switchTab({ url: '/pages/home/index' }) });
        }
      } else {
        const pages = getCurrentPages();
        if (pages.length > 1) wx.navigateBack();
        else wx.switchTab({ url: '/pages/home/index' });
      }
    }, 600);
  },

  /** 游客模式：先逛逛（FR-M1-03） */
  onBrowseAsGuest() {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      wx.navigateBack();
    } else {
      wx.switchTab({ url: '/pages/home/index' });
    }
  },

  onClose() {
    this.onBrowseAsGuest();
  }
});
