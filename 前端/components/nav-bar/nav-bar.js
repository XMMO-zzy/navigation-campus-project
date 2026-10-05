/**
 * components/nav-bar —— 自定义导航栏（蓝色主色 + 白底页面统一顶部）
 *
 * 用法：<nav-bar title="动态" back="{{true}}" />
 * 说明：本项目页面统一使用自定义导航栏，保证蓝底白字与全局配色一致
 */

Component({
  properties: {
    title: { type: String, value: '' },
    /** 是否显示返回箭头（非 Tab 页默认 true） */
    back: { type: Boolean, value: false },
    /** 背景色：primary（蓝色）| white（白底黑字） */
    theme: { type: String, value: 'white' },
    /** 是否显示底部细分割线 */
    border: { type: Boolean, value: true },
    /** 右侧占位宽度（rpx），用于放自定义按钮 */
    rightWidth: { type: Number, value: 0 }
  },

  data: {
    statusBarHeight: 20,
    navHeight: 44
  },

  lifetimes: {
    attached() {
      const app = getApp();
      const sbh = (app && app.globalData && app.globalData.statusBarHeight) || 20;
      this.setData({ statusBarHeight: sbh });
    }
  },

  methods: {
    onBack() {
      const pages = getCurrentPages();
      if (pages.length > 1) {
        wx.navigateBack();
      } else {
        wx.switchTab({ url: '/pages/home/index' });
      }
    }
  }
});
