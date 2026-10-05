/**
 * pages/me/blacklist —— 黑名单管理
 *
 * 来源：
 *   《10》FR-M7-06 黑名单管理 / FR-M2-11 屏蔽用户双向不可见
 *   《13》§3.1 GET /users/me/blacklist、PUT /users/me/blacklist、DELETE /users/{id}/block
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');

Page({
  data: {
    list: [],
    loaded: false,
    error: false
  },

  onLoad() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '黑名单管理' });
      return;
    }
  },

  onShow() {
    if (auth.isLogin()) this.load();
  },

  load() {
    this.setData({ error: false });
    return api.user.getBlacklist().then((data) => {
      const raw = (data && (data.list || data)) || [];
      this.setData({
        loaded: true,
        list: raw.map(item => Object.assign({}, item, {
          timeText: util.relativeTime(item.createdAt || item.blockedAt)
        }))
      });
    }).catch(() => this.setData({ loaded: true, error: true }));
  },

  onUnblock(e) {
    const id = e.currentTarget.dataset.id;
    const name = e.currentTarget.dataset.name;
    util.confirm(`解除对「${name}」的屏蔽？解除后你们将互相可见。`).then((ok) => {
      if (!ok) return;
      wx.showLoading({ title: '处理中', mask: true });
      api.user.unblock(id).then(() => {
        wx.hideLoading();
        util.toast('已解除屏蔽', 'success');
        this.setData({ list: this.data.list.filter(u => u.id !== id) });
      }).catch(() => wx.hideLoading());
    });
  },

  onRetry() {
    this.setData({ loaded: false });
    this.load();
  },

  onGoDiscover() {
    wx.switchTab({ url: '/pages/feed/index' });
  }
});
