/**
 * pages/me/setting-notify —— 消息通知设置
 *
 * 来源：
 *   《10》FR-M3-02 管理操作：分类开关 + 免打扰时段 + 例外会话
 *   《13》§3.3 GET/PUT /messages/settings
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');

Page({
  data: {
    loaded: false,
    saving: false,
    settings: {
      trade: true,      // 商品交易
      team: true,       // 组队协作
      interact: true,   // 内容互动
      system: true,     // 系统通知
      event: true,      // 活动竞赛
      platform: true,   // 平台公告
      dndEnabled: false,// 免打扰总开关
      dndStart: '22:00',
      dndEnd: '08:00',
      templateMsg: true // 微信模板消息兜底（FR-M3-09，P1）
    }
  },

  onLoad() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '消息设置' });
      return;
    }
    this.load();
  },

  load() {
    api.message.getSettings().then((d) => {
      const s = d || {};
      this.setData({
        loaded: true,
        settings: Object.assign({}, this.data.settings, s)
      });
    }).catch(() => this.setData({ loaded: true }));
  },

  onToggle(e) {
    const key = e.currentTarget.dataset.key;
    const value = e.detail.value;
    this.setData({ ['settings.' + key]: value }, () => this.save());
  },

  onTimeChange(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['settings.' + field]: e.detail.value }, () => this.save());
  },

  save() {
    if (this.data.saving) return;
    this.setData({ saving: true });
    api.message.updateSettings(this.data.settings).then(() => {
      this.setData({ saving: false });
    }).catch(() => this.setData({ saving: false }));
  },

  onNote() {
    wx.showModal({
      title: '关于通知',
      content: '关闭分类开关后，该类站内消息仍会记录在你的消息列表中，但不会有角标提示。免打扰时段内不推送微信模板消息。',
      showCancel: false,
      confirmColor: '#1E6FE8'
    });
  }
});
