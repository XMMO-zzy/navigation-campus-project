// pages/order/list.js — 我的订单（买到的/卖出的）
const api = require('../../framework/api');
const util = require('../../framework/util');
const auth = require('../../framework/auth');
const C = require('../../framework/constants');

const ROLE_TABS = [
  { key: 'buy', text: '我买到的' },
  { key: 'sell', text: '我卖出的' }
];

const STATUS_TABS = [
  { key: '', text: '全部' },
  { key: '20', text: '待付款' },
  { key: '30', text: '待面交' },
  { key: '40', text: '待确认' },
  { key: '50', text: '已完成' }
];

Page({
  data: {
    roleTabs: ROLE_TABS,
    statusTabs: STATUS_TABS,
    role: 'buy',
    status: '',
    list: [],
    page: 1,
    size: 20,
    total: 0,
    hasMore: true,
    loading: false,
    loaded: false,
    error: false
  },

  onLoad(options) {
    if (options && options.role && (options.role === 'buy' || options.role === 'sell')) {
      this.setData({ role: options.role });
    }
    if (options && options.status) {
      this.setData({ status: options.status });
    }
  },

  onShow() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '查看订单' });
      return;
    }
    this.reload();
  },

  onPullDownRefresh() {
    this.reload().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMore();
  },

  onTapRole(e) {
    const role = e.currentTarget.dataset.key;
    if (role === this.data.role) return;
    this.setData({ role: role }, () => this.reload());
  },

  onTapStatus(e) {
    const status = e.currentTarget.dataset.key;
    if (status === this.data.status) return;
    this.setData({ status: status }, () => this.reload());
  },

  reload() {
    return this.fetch(true);
  },

  loadMore() {
    if (!this.data.hasMore || this.data.loading) return;
    this.fetch(false);
  },

  fetch(reset) {
    if (this.data.loading) return Promise.resolve();
    const page = reset ? 1 : this.data.page + 1;
    if (!reset && !this.data.hasMore) return Promise.resolve();

    this.setData({ loading: true, error: false });

    return api.order.list(this.data.role, this.data.status ? Number(this.data.status) : undefined, page, this.data.size).then((data) => {
      const raw = (data && data.list) || [];
      const list = raw.map(this.decorate);
      this.setData({
        list: reset ? list : this.data.list.concat(list),
        page: page,
        total: (data && data.total) || list.length,
        hasMore: raw.length >= this.data.size,
        loading: false,
        loaded: true
      });
    }).catch(() => {
      this.setData({ loading: false, error: true, loaded: true });
    });
  },

  decorate(item) {
    const o = Object.assign({}, item);
    o.statusText = C.ORDER_STATUS_TEXT[o.status] || '未知';
    o.statusTag = C.ORDER_STATUS_TAG[o.status] || 'tag';
    o.priceText = util.formatPrice(o.price);
    o.createdAtText = util.relativeTime(o.createdAt);
    o.isBuyer = this.data.role === 'buy';
    // 主操作按钮（对齐《13》§3.4 状态机与 ◆ 面交双向确认）
    const isBuyer = o.isBuyer;
    if (o.status === 10) {
      o.actionText = isBuyer ? '' : '确认接单';
      o.actionType = isBuyer ? '' : 'sellerConfirm';
      o.tipText = isBuyer ? '等待卖家确认' : '';
    } else if (o.status === 20) {
      o.actionText = isBuyer ? '去付款' : '';
      o.actionType = isBuyer ? 'pay' : '';
      o.tipText = isBuyer ? '' : '等待买家付款';
      if (o.payTimeoutAt) o.deadlineText = util.deadlineLeft(o.payTimeoutAt);
    } else if (o.status === 30) {
      o.actionText = '确认面交完成';
      o.actionType = 'faceConfirm';
      o.tipText = '双方线下见面交易';
    } else if (o.status === 40) {
      o.actionText = '确认面交完成';
      o.actionType = 'faceConfirm';
      o.tipText = '对方已确认，等待你确认';
    } else if (o.status === 50) {
      o.actionText = '评价';
      o.actionType = 'review';
      o.tipText = '交易已完成';
    } else if (o.status === 60) {
      o.tipText = '订单已取消';
    } else if (o.status === 70) {
      o.tipText = '超时关闭';
    }
    return o;
  },

  onTapOrder(e) {
    const no = e.currentTarget.dataset.no;
    wx.navigateTo({ url: `/pages/order/detail?no=${no}` });
  },

  onAction(e) {
    const dataset = e.currentTarget.dataset;
    const no = dataset.no;
    const type = dataset.type;
    const item = this.data.list.find(o => o.orderNo === no);
    if (!item) return;

    if (type === 'sellerConfirm') {
      this.doAction(() => api.order.sellerConfirm(no), '确认接单', '已接单，等待买家付款');
    } else if (type === 'pay') {
      wx.navigateTo({ url: `/pages/order/detail?no=${no}&action=pay` });
    } else if (type === 'faceConfirm') {
      this.doAction(() => api.order.faceConfirm(no), '确认面交', '已确认，等待双方完成确认');
    } else if (type === 'review') {
      wx.navigateTo({ url: `/pages/order/detail?no=${no}&action=comment` });
    }
  },

  doAction(fn, title, successMsg) {
    wx.showModal({
      title: title,
      content: `确认执行「${title}」操作？`,
      confirmColor: '#1E6FE8',
      success: (res) => {
        if (!res.confirm) return;
        wx.showLoading({ title: '处理中', mask: true });
        fn().then(() => {
          wx.hideLoading();
          util.toast(successMsg, 'success');
          setTimeout(() => this.reload(), 600);
        }).catch(() => wx.hideLoading());
      }
    });
  }
});
