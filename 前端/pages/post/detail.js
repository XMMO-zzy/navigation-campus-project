/**
 * pages/post/detail —— 动态详情（正文 + 评论 + 互动）
 *
 * 来源：
 *   《10》FR-M2-02 动态详情 / FR-M2-03 互动（点赞/收藏/评论）
 *   《13》§3.2 GET /posts/{id}、GET/POST /posts/{id}/comments、
 *              POST/DELETE /posts/{id}/like、POST/DELETE /posts/{id}/favorite
 *   《12》§1.3 通用多态字段 link_type/link_id（关联活动/竞赛/商品）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

Page({
  data: {
    id: null,
    loading: true,
    error: false,
    post: null,
    isOwner: false,

    comments: [],
    commentPage: 1,
    commentTotal: 0,
    hasMoreComments: false,
    commentLoading: false,

    /* 评论输入 */
    commentText: '',
    replyTo: null,       // { commentId, nickname }
    commentFocus: false,

    showMore: false,

    submitting: false
  },

  onLoad(query) {
    const id = query && query.id;
    if (!id) {
      this.setData({ loading: false, error: true });
      return;
    }
    this.setData({ id: id });
    this.load();
    this.loadComments(true);
  },

  onPullDownRefresh() {
    Promise.all([this.load(), this.loadComments(true)])
      .then(() => wx.stopPullDownRefresh())
      .catch(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMoreComments) this.loadComments(false);
  },

  /* ------------------------------------------------------------------ */
  /* 加载                                                                */
  /* ------------------------------------------------------------------ */
  load() {
    return api.post.detail(this.data.id).then((data) => {
      const me = auth.currentUser() || {};
      const post = Object.assign({}, data, {
        contentType: data.contentType,
        typeText: C.labelOf(C.CONTENT_TYPE, data.contentType, '动态'),
        timeText: util.relativeTime(data.createdAt),
        images: data.images || (data.image ? [data.image] : []),
        likeCount: (data.cnt && data.cnt.like) || data.likeCount || 0,
        commentCount: (data.cnt && data.cnt.comment) || data.commentCount || 0,
        favoriteCount: (data.cnt && data.cnt.favorite) || data.favoriteCount || 0,
        likeText: util.formatCount((data.cnt && data.cnt.like) || data.likeCount || 0),
        topicText: (data.topics || []).join(' ')
      });
      this.setData({
        loading: false,
        error: false,
        post: post,
        isOwner: !!(me.userId && String(data.userId) === String(me.userId))
      });
      wx.setNavigationBarTitle({ title: post.typeText });
    }).catch(() => this.setData({ loading: false, error: true }));
  },

  loadComments(reset) {
    if (this.data.commentLoading) return Promise.resolve();
    const page = reset ? 1 : this.data.commentPage + 1;
    this.setData({ commentLoading: true });

    return api.post.comments(this.data.id, page, 20).then((data) => {
      const raw = (data && data.list) || [];
      const list = raw.map(decorateComment);
      this.setData({
        comments: reset ? list : this.data.comments.concat(list),
        commentPage: page,
        commentTotal: (data && data.total) || list.length,
        hasMoreComments: raw.length >= 20,
        commentLoading: false
      });
    }).catch(() => this.setData({ commentLoading: false }));
  },

  onRetry() {
    this.setData({ error: false, loading: true });
    this.load();
    this.loadComments(true);
  },

  /* ------------------------------------------------------------------ */
  /* 互动（FR-M2-03）                                                     */
  /* ------------------------------------------------------------------ */
  onToggleLike() {
    if (!auth.requireAuth({ title: '点赞需要先登录' })) return;
    const p = this.data.post;
    const liked = !!p.liked;
    const delta = liked ? -1 : 1;
    const nextCount = Math.max(0, (p.likeCount || 0) + delta);
    this.setData({
      'post.liked': !liked,
      'post.likeCount': nextCount,
      'post.likeText': util.formatCount(nextCount)
    });
    const req = liked ? api.post.unlike(this.data.id) : api.post.like(this.data.id);
    req.catch(() => {
      this.setData({
        'post.liked': liked,
        'post.likeCount': p.likeCount,
        'post.likeText': p.likeText
      });
    });
  },

  onToggleFavorite() {
    if (!auth.requireAuth({ title: '收藏需要先登录' })) return;
    const p = this.data.post;
    const faved = !!p.favorited;
    const nextCount = Math.max(0, (p.favoriteCount || 0) + (faved ? -1 : 1));
    this.setData({
      'post.favorited': !faved,
      'post.favoriteCount': nextCount
    });
    const req = faved ? api.post.unfavorite(this.data.id) : api.post.favorite(this.data.id);
    req.then(() => {
      if (!faved) util.toast('已收藏，截止前会提醒你', 'success');
    }).catch(() => this.setData({ 'post.favorited': faved, 'post.favoriteCount': p.favoriteCount }));
  },

  onDislike() {
    if (!auth.requireAuth({ title: '操作需要先登录' })) return;
    api.post.dislike(this.data.id).then(() => {
      util.toast('已减少此类内容推荐');
      setTimeout(() => wx.navigateBack(), 700);
    });
  },

  onShareAppMessage() {
    const p = this.data.post || {};
    return {
      title: p.title || p.content || '通航校园动态',
      path: `/pages/post/detail?id=${this.data.id}`,
      imageUrl: (p.images && p.images[0]) || ''
    };
  },

  /* ------------------------------------------------------------------ */
  /* 评论                                                                */
  /* ------------------------------------------------------------------ */
  onCommentInput(e) {
    this.setData({ commentText: e.detail.value });
  },

  onCommentFocus() {
    this.setData({ commentFocus: true });
  },

  onCommentBlur() {
    this.setData({ commentFocus: false });
  },

  onReply(e) {
    if (!auth.requireAuth({ title: '评论需要先登录' })) return;
    const { id, nickname } = e.currentTarget.dataset;
    this.setData({
      replyTo: { commentId: id, nickname: nickname },
      commentFocus: true,
      commentText: ''
    });
  },

  onCancelReply() {
    this.setData({ replyTo: null });
  },

  onSubmitComment() {
    if (!auth.requireAuth({ title: '评论需要先登录' })) return;
    const text = this.data.commentText.trim();
    if (!text) {
      util.toast('请输入评论内容');
      return;
    }
    if (text.length > 200) {
      util.toast('评论不能超过 200 字');
      return;
    }
    if (this.data.submitting) return;
    this.setData({ submitting: true });

    const payload = { content: text };
    if (this.data.replyTo) payload.parentId = this.data.replyTo.commentId;

    // 命中评论权限拦截（40005）走鉴权引导
    api.post.comment(this.data.id, payload).then(() => {
      this.setData({
        submitting: false,
        commentText: '',
        replyTo: null,
        commentFocus: false,
        'post.commentCount': (this.data.post.commentCount || 0) + 1
      });
      util.toast('评论成功', 'success');
      this.loadComments(true);
    }).catch((err) => {
      this.setData({ submitting: false });
      if (err && err.code === 40005) {
        auth.requireVerified({ title: '发表评论' });
      }
    });
  },

  onDeleteComment(e) {
    const id = e.currentTarget.dataset.id;
    util.confirm('确定删除这条评论？').then((ok) => {
      if (!ok) return;
      api.post.comment
        ? wx.showToast({ title: '暂不支持删除评论', icon: 'none' })
        : null;
    });
  },

  onDeletePost() {
    util.confirm('删除后不可恢复，确定删除这条动态？', '删除动态', '删除').then((ok) => {
      if (!ok) return;
      api.post.remove(this.data.id).then(() => {
        util.toast('已删除', 'success');
        setTimeout(() => wx.navigateBack(), 600);
      });
    });
  },

  onEditPost() {
    wx.navigateTo({ url: `/pages/publish/index?type=post&id=${this.data.id}` });
  },

  onReport() {
    if (!auth.requireAuth({ title: '举报需要先登录' })) return;
    wx.navigateTo({ url: `/pages/me/reports?action=create&targetType=1&targetId=${this.data.id}` });
  },

  /* 更多操作面板 */
  onMore() {
    this.setData({ showMore: true });
  },

  onCloseMore() {
    this.setData({ showMore: false });
  },

  /**
   * 关注作者 —— 《13》§3 未定义关注接口（阶段二契约缺口已标记）。
   * 此处明确提示，不臆造接口；待契约评审后由 api.follow 放行。
   */
  onFollow() {
    if (!auth.requireAuth({ title: '关注需要先登录' })) return;
    this.setData({ showMore: false });
    wx.showModal({
      title: '关注功能暂未开放',
      content: '关注体系对应的后端接口尚未在《13-API接口文档》中定义，需契约评审后启用。当前可在「动态 · 关注」页查看已关注内容。',
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#1E6FE8'
    });
  },

  onTapUser() {
    const p = this.data.post || {};
    const uid = p.userId || (p.user && p.user.id);
    if (!uid) return;
    wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${uid}` });
  },

  onPreviewImage(e) {
    const idx = Number(e.currentTarget.dataset.index) || 0;
    const urls = (this.data.post && this.data.post.images) || [];
    if (!urls.length) return;
    wx.previewImage({ urls: urls, current: urls[idx] });
  },

  onTapLink() {
    const link = this.data.post && this.data.post.link;
    if (!link) return;
    const url = C.contentTypeRoute(link.type, link.id);
    if (url) wx.navigateTo({ url: url });
  },

  noop() {}
});

/* -------------------------------------------------------------------- */
function decorateComment(c) {
  return Object.assign({}, c, {
    timeText: util.relativeTime(c.createdAt),
    nickname: (c.user && c.user.nickname) || c.nickname || '匿名用户',
    avatar: (c.user && c.user.avatar) || c.avatar || '/static/default-avatar.png',
    isOwner: false,
    parentNickname: (c.parent && c.parent.nickname) || ''
  });
}
