/**
 * components/feed-card —— 混合信息流卡片（六类注册制渲染）
 *
 * 来源：
 *   《13》§4 GET /feed 响应结构（卡片字段契约）
 *   《04-思维导图（重构版）》动态信息流：动态卡片 / 二手商品 / 找搭子 / 个人内容 / 活动与竞赛 / 校园
 *   《B-前端-动态与发布》§1：卡片组件注册制 components/feed-cards/<type>，新类型不影响已有渲染
 *
 * 卡片结构（《04》动态卡片）：发布者信息 + 正文 + 互动数据 + 卡片操作
 *
 * 用法：<feed-card item="{{item}}" bind:action="onCardAction" bind:tapcard="onTapCard" />
 *   action 事件 detail: { action, item }  action ∈ like|comment|favorite|share|dislike|report
 */

const { CONTENT_TYPE, POST_TYPE_TEXT, ORDER_STATUS_TEXT } = require('../../framework/constants');
const util = require('../../framework/util');

Component({
  properties: {
    /** 卡片数据（结构严格对齐《13》§4 feed 示例） */
    item: { type: Object, value: {} },
    /** 是否展示操作栏 */
    showActions: { type: Boolean, value: true }
  },

  data: {
    /** 归一化后的视图数据 */
    view: {},
    /** 六类卡片的渲染类型 key */
    cardType: 'post',
    timeText: ''
  },

  observers: {
    'item': function (item) {
      if (!item) return;
      this.buildView(item);
    }
  },

  lifetimes: {
    attached() {
      if (this.data.item) this.buildView(this.data.item);
    }
  },

  methods: {
    /**
     * 把接口数据归一化为视图模型
     * 六类：post（动态，含个人内容子类）/ goods（商品）/ buddy（搭子）/
     *      activity|competition（活动与竞赛，走 link 渲染）/ notice（校园/官方）
     */
    buildView(item) {
      const type = item.contentType;
      let cardType = 'post';

      if (type === CONTENT_TYPE.GOODS) cardType = 'goods';
      else if (type === CONTENT_TYPE.BUDDY) cardType = 'buddy';
      else if (type === CONTENT_TYPE.ACTIVITY) cardType = 'activity';
      else if (type === CONTENT_TYPE.COMPETITION) cardType = 'competition';
      else if (type === CONTENT_TYPE.NOTICE) cardType = 'notice';

      const view = Object.assign({}, item, {
        images: Array.isArray(item.images) ? item.images.slice(0, 9) : [],
        imageCount: Array.isArray(item.images) ? item.images.length : 0,
        topics: Array.isArray(item.topics) ? item.topics.slice(0, 3) : [],
        cnt: item.cnt || {},
        user: item.user || {},
        priceText: item.price !== undefined && item.price !== null ? util.formatPrice(item.price) : '',
        condText: item.cond || '',
        postTypeText: item.postType ? (POST_TYPE_TEXT[item.postType] || '') : '',
        likeText: util.formatCount((item.cnt && item.cnt.like) || 0),
        commentText: util.formatCount((item.cnt && item.cnt.comment) || 0),
        favoriteText: util.formatCount((item.cnt && item.cnt.favorite) || 0),
        deadlineText: ''
      });

      // 截止剩余时间（《13》§4 feed 示例 link.deadlineLeft）
      if (item.link && item.link.deadlineLeft) {
        view.deadlineText = item.link.deadlineLeft;
      } else if (item.deadline) {
        view.deadlineText = util.deadlineLeft(item.deadline);
      }

      this.setData({
        view,
        cardType,
        timeText: util.relativeTime(item.createdAt)
      });
    },

    /** 卡片主体点击 → 触发父级路由 */
    onTapCard() {
      this.triggerEvent('tapcard', { item: this.data.item });
    },

    /** 点击发布者 */
    onTapUser(e) {
      const uid = (this.data.item.user && this.data.item.user.id) ||
        (this.data.item.userId);
      if (!uid) return;
      wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${uid}`, fail: () => {} });
    },

    /** 操作栏：点赞 */
    onLike() { this.emit('like'); },
    onComment() { this.emit('comment'); },
    onFavorite() { this.emit('favorite'); },
    onShare() { this.emit('share'); },
    onDislike() { this.emit('dislike'); },
    onReport() { this.emit('report'); },

    /** 更多操作（不感兴趣 / 举报） */
    onMore() {
      const that = this;
      wx.showActionSheet({
        itemList: ['不感兴趣', '举报'],
        success(res) {
          if (res.tapIndex === 0) that.emit('dislike');
          else if (res.tapIndex === 1) that.emit('report');
        },
        fail: () => {}
      });
    },

    /** 图片预览 */
    onPreviewImage(e) {
      const idx = e.currentTarget.dataset.index || 0;
      const urls = this.data.view.images || [];
      if (!urls.length) return;
      wx.previewImage({ urls, current: urls[idx] });
    },

    emit(action) {
      this.triggerEvent('action', { action, item: this.data.item });
    }
  }
});
