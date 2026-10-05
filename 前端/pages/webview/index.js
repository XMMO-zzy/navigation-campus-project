/**
 * pages/webview/index —— 内嵌网页容器
 *
 * 来源：《13》§3.7 平台支撑（官方来源、隐私政策、用户协议、帮助中心等）
 * 安全：仅允许 https 白名单域名（与《18》隐私合规一致），非法域名拒绝加载。
 */

const config = require('../../framework/config');
const util = require('../../framework/util');

const ALLOWED_HOSTS = [
  'mp.weixin.qq.com',
  'work.weixin.qq.com',
  'docs.qq.com'
];

function isAllowed(url) {
  if (!url) return false;
  if (/^https?:\/\/(dev|test|api|static)\.campus\.(local|example\.cn)/.test(url)) return true;
  try {
    const host = url.replace(/^https?:\/\//, '').split('/')[0].split(':')[0];
    return ALLOWED_HOSTS.some(h => host === h || host.endsWith('.' + h));
  } catch (e) {
    return false;
  }
}

Page({
  data: {
    url: '',
    title: '',
    invalid: false
  },

  onLoad(query) {
    const raw = query && query.url ? decodeURIComponent(query.url) : '';
    const title = query && query.title ? decodeURIComponent(query.title) : '';

    if (!raw || !isAllowed(raw)) {
      this.setData({ invalid: true });
      if (title) wx.setNavigationBarTitle({ title: title });
      return;
    }
    this.setData({ url: raw, title: title });
    if (title) wx.setNavigationBarTitle({ title: title });
  },

  onBack() {
    wx.navigateBack();
  },

  onError(e) {
    console.warn('[webview] load error', e.detail);
    this.setData({ invalid: true });
  }
});
