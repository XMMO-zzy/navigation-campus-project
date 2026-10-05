// pages/order/detail.js — 订单详情（交易状态机）
const api = require('../../framework/api');
const util = require('../../framework/util');
const auth = require('../../framework/auth');
const C = require('../../framework/constants');

// 各状态可执行动作（按角色）—— 对齐《13》§3.4 与状态机 10→20→30→40→50
function actionsOf(order, isBuyer) {
  const acts = [];
  const s = order.status;
  if (s === 10) {
    if (isBuyer) {
      acts.push({ type: 'cancel', text: '取消订单', ghost: true });
    } else {
      acts.push({ type: 'cancel', text: '取消订单', ghost: true });
      acts.push({ type: 'sellerConfirm', text: '确认接单', primary: true });
    }
  } else if (s === 20) {
    if (isBuyer) {
      acts.push({ type: 'cancel', text: '取消订单', ghost: true });
      acts.push({ type: 'pay', text: '去付款', primary: true });
    }
  } else if (s === 30) {
    if (order.myFaceConfirmed) {
      acts.push({ type: 'none', text: '等待对方确认面交', disabled: true });
    } else {
      acts.push({ type: 'faceConfirm', text: '确认已面交', primary: true });
    }
  } else if (s === 40) {
    if (order.myFaceConfirmed) {
      acts.push({ type: 'none', text: '等待对方确认', disabled: true });
    } else {
      acts.push({ type: 'faceConfirm', text: '确认面交完成', primary: true });
    }
  } else if (s === 50) {
    if (!order.myReviewed) {
      acts.push({ type: 'review', text: '评价对方', primary: true });
    }
    acts.push({ type: 'report', text: '举报', ghost: true });
  }
  return acts;
}

Page({
  data: {
    orderNo: '',
    order: null,
    isBuyer: false,
    actions: [],
    timeline: [],
    loaded: false,
    error: false,
    contactVisible: false,
    showPaySheet: false,
    showCommentSheet: false,
    commentScore: 5,
    commentContent: '',
    paying: false
  },

  onLoad(options) {
    this.setData({ orderNo: (options && options.no) || '' });
    if (options && options.action === 'pay') {
      this.setData({ showPaySheet: true });
    }
    if (options && options.action === 'comment') {
      this.setData({ showCommentSheet: true });
    }
  },

  onShow() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '查看订单' });
      return;
    }
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    if (!this.data.orderNo) {
      this.setData({ loaded: true, error: true });
      return Promise.resolve();
    }
    return api.order.detail(this.data.orderNo).then((data) => {
      const me = auth.currentUser() || {};
      const isBuyer = String(data.buyerId) === String(me.userId || me.id);
      const order = Object.assign({}, data, {
        isBuyer: isBuyer,
        statusText: C.ORDER_STATUS_TEXT[data.status] || '未知',
        statusTag: C.ORDER_STATUS_TAG[data.status] || 'tag',
        priceText: util.formatPrice(data.price),
        goodsPriceText: util.formatPrice(data.goodsPrice || data.price),
        createdAtText: util.formatTime(data.createdAt),
        deadlineText: data.payTimeoutAt ? util.deadlineLeft(data.payTimeoutAt) : '',
        myFaceConfirmed: !!data.myFaceConfirmed,
        myReviewed: !!data.myReviewed
      });
      const actions = actionsOf(order, isBuyer);
      this.setData({
        order: order,
        isBuyer: isBuyer,
        actions: actions,
        timeline: this.buildTimeline(order),
        loaded: true,
        error: false
      });
    }).catch(() => {
      this.setData({ loaded: true, error: true });
    });
  },

  buildTimeline(o) {
    const nodes = [
      { key: 10, text: '买家提交订单', time: o.createdAt },
      { key: 20, text: '卖家确认接单', time: o.sellerConfirmAt },
      { key: 30, text: '买家完成付款', time: o.paidAt },
      { key: 40, text: '双方线下见面交易', time: o.faceMeetAt },
      { key: 50, text: '交易完成', time: o.finishedAt }
    ];
    const cur = o.status;
    let marked = false;
    return nodes.map((n) => {
      let state = 'wait';
      if (cur >= n.key) {
        state = 'done';
      } else if (!marked && n.key > cur && cur !== 60 && cur !== 70) {
        state = 'active';
        marked = true;
      }
      if (cur === 60 || cur === 70) {
        state = n.key <= 10 ? 'done' : 'wait';
      }
      return Object.assign({}, n, { state: state, timeText: n.time ? util.formatTime(n.time) : '' });
    });
  },

  /* ---------- 交互 ---------- */
  onTapGoods() {
    if (this.data.order && this.data.order.goodsId) {
      wx.navigateTo({ url: `/pages/goods/detail?id=${this.data.order.goodsId}` });
    }
  },

  onToggleContact() {
    const o = this.data.order;
    if (!o || !o.contact) {
      util.toast('对方未填写联系方式');
      return;
    }
    // 只有付款后状态才允许查看联系方式
    if (o.status === 10) {
      util.toast('卖家接单后可查看联系方式');
      return;
    }
    if (o.status === 20 && !this.data.isBuyer) {
      util.toast('买家付款后可查看联系方式');
      return;
    }
    this.setData({ contactVisible: !this.data.contactVisible });
  },

  onCopyContact() {
    const o = this.data.order;
    if (o && o.contact) util.copy(o.contact);
  },

  onCopyOrderNo() {
    util.copy(this.data.orderNo);
  },

  onTapAction(e) {
    const type = e.currentTarget.dataset.type;
    const o = this.data.order;
    if (!o) return;
    const map = {
      sellerConfirm: { fn: () => api.order.sellerConfirm(o.orderNo), title: '确认接单', ok: '已接单，等待买家付款', msg: '确认接受这笔订单？接单后请与买家约定面交时间地点。' },
      cancel: { fn: () => api.order.cancel(o.orderNo, this.data.isBuyer ? '买家主动取消' : '卖家主动取消'), title: '取消订单', ok: '订单已取消', msg: '确认取消这笔订单？' },
      faceConfirm: { fn: () => api.order.faceConfirm(o.orderNo), title: '确认面交', ok: '已确认，等待双方完成确认', msg: '请确认已与对方完成线下见面交易。双方均确认后订单自动完成。' }
    };
    if (type === 'pay') {
      this.setData({ showPaySheet: true });
      return;
    }
    if (type === 'review') {
      this.setData({ showCommentSheet: true });
      return;
    }
    if (type === 'report') {
      wx.navigateTo({ url: `/pages/me/reports?bizType=10&bizId=${o.orderNo}` });
      return;
    }
    if (type === 'none') return;

    const cfg = map[type];
    if (!cfg) return;
    wx.showModal({
      title: cfg.title,
      content: cfg.msg,
      confirmColor: '#1E6FE8',
      success: (res) => {
        if (!res.confirm) return;
        wx.showLoading({ title: '处理中', mask: true });
        cfg.fn().then(() => {
          wx.hideLoading();
          util.toast(cfg.ok, 'success');
          setTimeout(() => this.load(), 700);
        }).catch(() => wx.hideLoading());
      }
    });
  },

  /* ---------- 支付（校内当面交易，无真实支付通道，仅记录付款状态） ---------- */
  onClosePaySheet() {
    this.setData({ showPaySheet: false });
  },
  onPayMethod(e) {
    const method = e.currentTarget.dataset.method;
    this.setData({ payMethod: method });
  },
  onConfirmPay() {
    if (this.data.paying) return;
    const method = this.data.payMethod || 'wechat';
    this.setData({ paying: true });
    wx.showLoading({ title: '提交中', mask: true });
    // 《13》§3.4 未定义独立付款接口，付款动作由面交异步完成；
    // 此处仅做界面确认，引导用户线下完成支付。
    setTimeout(() => {
      wx.hideLoading();
      this.setData({ paying: false, showPaySheet: false });
      util.toast('请按约定方式完成支付后确认面交', 'none', 2200);
    }, 500);
  },

  /* ---------- 互评（POST /orders/{no}/review） ---------- */
  onOpenComment() {
    this.setData({ showCommentSheet: true });
  },
  onCloseCommentSheet() {
    this.setData({ showCommentSheet: false });
  },
  onTapScore(e) {
    this.setData({ commentScore: Number(e.currentTarget.dataset.score) });
  },
  onCommentInput(e) {
    this.setData({ commentContent: e.detail.value });
  },
  onSubmitComment() {
    const content = this.data.commentContent.trim();
    if (!content) {
      util.toast('请填写评价内容');
      return;
    }
    wx.showLoading({ title: '提交中', mask: true });
    api.order.review(this.data.orderNo, { score: this.data.commentScore, content: content }).then(() => {
      wx.hideLoading();
      this.setData({ showCommentSheet: false, commentContent: '' });
      util.toast('评价成功', 'success');
      setTimeout(() => this.load(), 700);
    }).catch(() => wx.hideLoading());
  },

  onRetry() {
    this.setData({ loaded: false, error: false });
    this.load();
  },

  noop() {},

  onContact() {
    const o = this.data.order;
    if (!o) return;
    wx.showModal({
      title: '联系对方',
      content: '如需沟通交易细节，可在消息中与对方会话，联系方式请当面核实。',
      showCancel: false,
      confirmColor: '#1E6FE8'
    });
  },

  onGoMessage() {
    wx.switchTab({ url: '/pages/message/index' });
  }
});
