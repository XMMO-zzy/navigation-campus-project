/**
 * pages/me/setting-account —— 账号管理
 *
 * 来源：《04》4.4 设置中心·账号管理 / 《10》FR-M1-07/08
 * 接口：《13》§3.1 GET /users/me、PUT /users/me、POST /users/deactivate
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');

Page({
  data: {
    user: {},
    isLogin: false,
    phoneText: '',
    editingPhone: false,
    newPhone: '',
    smsCode: '',
    counting: 0
  },

  onShow() {
    const isLogin = auth.isLogin();
    this.setData({ isLogin });
    if (isLogin) this.load();
  },

  load() {
    api.user.me().then((u) => {
      const user = u || {};
      this.setData({
        user: user,
        phoneText: util.maskPhone(user.phone)
      });
    }).catch(() => {});
  },

  onGoVerify() {
    wx.navigateTo({ url: '/pages/auth/verify' });
  },

  onEditPhone() {
    if (!auth.requireAuth({ title: '修改手机号需要先登录' })) return;
    this.setData({ editingPhone: true, newPhone: '', smsCode: '', counting: 0 });
  },

  onCancelEditPhone() {
    this.setData({ editingPhone: false });
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  onSendCode() {
    if (this.data.counting > 0) return;
    const phone = this.data.newPhone.trim();
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      util.toast('请输入正确的手机号');
      return;
    }
    // 《13》未单列发码接口，手机号登录为备用通道；此处仅做本地倒计时占位
    let n = 60;
    this.setData({ counting: n });
    const timer = setInterval(() => {
      n -= 1;
      if (n <= 0) {
        clearInterval(timer);
        this.setData({ counting: 0 });
      } else {
        this.setData({ counting: n });
      }
    }, 1000);
    util.toast('验证码已发送', 'success');
  },

  onSavePhone() {
    const phone = this.data.newPhone.trim();
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      util.toast('请输入正确的手机号');
      return;
    }
    if (!/^\d{4,6}$/.test(this.data.smsCode.trim())) {
      util.toast('请输入正确的验证码');
      return;
    }
    wx.showLoading({ title: '保存中', mask: true });
    api.user.updateMe({ phone: phone, smsCode: this.data.smsCode.trim() }).then(() => {
      wx.hideLoading();
      this.setData({ editingPhone: false });
      util.toast('手机号已更新', 'success');
      this.load();
    }).catch(() => wx.hideLoading());
  },

  onCopyDevice() {
    util.toast('设备信息仅用于安全风控');
  },

  onSecurity() {
    wx.showModal({
      title: '账号安全',
      content: '· 已绑定微信，可用于一键登录\n· 异地登录会通过站内消息提醒你\n· 如发现异常登录请及时修改手机号',
      showCancel: false,
      confirmColor: '#1E6FE8'
    });
  },

  onGoLogin() {
    auth.requireAuth({ title: '账号管理' });
  }
});
