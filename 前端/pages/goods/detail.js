/**
 * pages/goods/detail —— 商品详情（含下单入口）
 *
 * 来源：
 *   《10》FR-M4-02 商品详情（联系方式按权限脱敏）/ FR-M4-04 下单
 *   《13》§3.4 GET /goods/{id}、POST /orders
 *   《17》§5 安全提示：付款前请勿脱离平台私聊转账；首版仅信息撮合
 *   《H》§4.3 安全可视：联系方式解锁前展示锁形提示与原因文案
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const { GOODS_COND, GOODS_CATEGORY, labelOf } = require('../../framework/constants');

Page({
  data: {
    id: null,
    loading: true,
    error: false,
    goods: null,

    /** 图片轮播索引 */
    current: 0,
    images: [],

    /** 是否为本人发布 */
    isOwner: false,
    /** 联系方式是否已解锁（卖家确认后，FR-M4-04） */
    contactUnlocked: false,

    /** 收藏状态 */
    favorited: false,

    submitting: false
  },

  onLoad(query) {
    const id = query && query.id;
    if (!id) {
      this.setData({ loading: false, error: true });
      return;
    }
    this.setData({ id });
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  /* ------------------------------------------------------------------ */
  load() {
    this.setData({ loading: true, error: false });
    return api.goods.detail(this.data.id)
      .then(g => {
        const goods = g || {};
        const images = Array.isArray(goods.images) && goods.images.length
          ? goods.images
          : (goods.image ? [goods.image] : []);

        const user = auth.currentUser();
        this.setData({
          loading: false,
          goods: Object.assign({}, goods, {
            priceText: util.formatPrice(goods.price),
            origPriceText: goods.origPrice ? util.formatPrice(goods.origPrice) : '',
            condText: labelOf(GOODS_COND, goods.cond, ''),
            categoryText: labelOf(GOODS_CATEGORY, goods.categoryId, ''),
            timeText: util.relativeTime(goods.createdAt),
            seller: goods.seller || goods.user || {}
          }),
          images,
          isOwner: !!(user && user.id && goods.userId === user.id),
          favorited: !!goods.favorited,
          contactUnlocked: !!goods.contactUnlocked || !!goods.contact
        });
      })
      .catch(() => this.setData({ loading: false, error: true }));
  },

  onRetry() { this.load(); },

  /* ------------------------------------------------------------------ */
  /* 图片与交互                                                          */
  /* ------------------------------------------------------------------ */
  onSwiperChange(e) {
    this.setData({ current: e.detail.current });
  },

  onPreviewImage(e) {
    const idx = e.currentTarget.dataset.index || 0;
    if (!this.data.images.length) return;
    wx.previewImage({ urls: this.data.images, current: this.data.images[idx] });
  },

  onTapSeller() {
    const seller = this.data.goods && this.data.goods.seller;
    if (!seller || !seller.id) return;
    wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${seller.id}` });
  },

  /* ------------------------------------------------------------------ */
  /* 收藏（FR-M2-03）                                                     */
  /* ------------------------------------------------------------------ */
  onToggleFavorite() {
    if (!auth.requireAuth({ title: '收藏需要先登录' })) return;
    const faved = this.data.favorited;
    this.setData({ favorited: !faved });
    const req = faved ? api.goods.unfavorite(this.data.id) : api.goods.favorite(this.data.id);
    req.catch(() => this.setData({ favorited: faved }));
  },

  /* ------------------------------------------------------------------ */
  /* 下单（FR-M4-04）                                                     */
  /* ------------------------------------------------------------------ */
  onTapOrder() {
    if (!auth.requireAuth({ title: '下单需要先登录' })) return;
    if (this.data.isOwner) {
      util.toast('不能购买自己发布的商品');
      return;
    }
    const status = this.data.goods.status;
    // 状态冲突（40007）：非在售商品不可下单
    if (status !== undefined && status !== 1) {
      util.toast('该商品当前不可下单');
      return;
    }
    if (this.data.submitting) return;

    const self = this;
    util.confirm(
      `确认购买「${this.data.goods.title}」？\n下单后需卖家确认，确认后才可见联系方式。`,
      '确认下单', '确认下单'
    ).then(ok => {
      if (!ok) return;
      self.setData({ submitting: true });
      api.order.create(self.data.id)
        .then(data => {
          self.setData({ submitting: false });
          util.toast('下单成功，等待卖家确认', 'success');
          const no = (data && data.orderNo) || '';
          setTimeout(() => {
            if (no) wx.navigateTo({ url: `/pages/order/detail?no=${no}` });
            else wx.navigateTo({ url: '/pages/order/list?role=buy' });
          }, 700);
        })
        .catch(() => self.setData({ submitting: false }));
    });
  },

  /* ------------------------------------------------------------------ */
  /* 联系卖家（联系方式解锁，FR-M4-04 防骚扰）                              */
  /* ------------------------------------------------------------------ */
  onContact() {
    if (!auth.requireAuth({ title: '查看联系方式需要先登录' })) return;
    if (!this.data.contactUnlocked) {
      wx.showModal({
        title: '联系方式暂未解锁',
        content: '为保护双方隐私、防止骚扰，卖家确认订单后才会展示联系方式。请先点击「我想要」下单。',
        showCancel: false,
        confirmText: '我知道了',
        confirmColor: '#1E6FE8'
      });
      return;
    }
    const contact = this.data.goods.contact;
    if (contact) {
      util.copy(contact);
    } else {
      util.toast('联系方式暂不可用');
    }
  },

  /* ------------------------------------------------------------------ */
  /* 管理（本人）：下架 / 编辑                                            */
  /* ------------------------------------------------------------------ */
  onManage() {
    const self = this;
    wx.showActionSheet({
      itemList: ['编辑商品', '下架商品', '删除商品'],
      success(res) {
        if (res.tapIndex === 0) {
          wx.navigateTo({ url: `/pages/goods/publish?id=${self.data.id}` });
        } else if (res.tapIndex === 1) {
          util.confirm('下架后商品将不再展示，确定下架？').then(ok => {
            if (!ok) return;
            api.goods.update(self.data.id, { status: 2 }).then(() => {
              util.toast('已下架', 'success');
              self.load();
            });
          });
        } else if (res.tapIndex === 2) {
          util.confirm('删除后不可恢复，确定删除？', '删除商品', '删除').then(ok => {
            if (!ok) return;
            api.goods.remove(self.data.id).then(() => {
              util.toast('已删除', 'success');
              setTimeout(() => wx.navigateBack(), 600);
            });
          });
        }
      },
      fail: () => {}
    });
  },

  /** 举报（FR-M7-03） */
  onReport() {
    if (!auth.requireAuth({ title: '举报需要先登录' })) return;
    wx.navigateTo({
      url: `/pages/me/reports?action=create&targetType=2&targetId=${this.data.id}`
    });
  },

  onShareAppMessage() {
    const g = this.data.goods || {};
    return {
      title: g.title || '校园二手好物',
      path: `/pages/goods/detail?id=${this.data.id}`,
      imageUrl: this.data.images[0] || ''
    };
  }
});
