/**
 * pages/publish/index —— 发布中心（动态发布主体 + 各业务发布入口）
 *
 * 来源：
 *   《10》FR-M2-04 发布动态：文字 + 多图（预览/排序/删除）+ 话题绑定 + 关联内容
 *   《10》FR-M2-05 发布设置：可见范围、评论权限
 *   《13》§3.2 POST /posts、§3.7 PUT /drafts/{type}（草稿，P1）
 *   《12》visible_scope / comment_permission 枚举（constants.js）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

const MAX_IMAGES = 9;
const MAX_CONTENT = 1000;

/* 可切换的发布类型入口（对齐 M2/M4/M5/M6 发布） */
const ENTRIES = [
  { key: 'post', glyph: '📝', label: '发动态', color: '#1E6FE8', url: '' },
  { key: 'goods', glyph: '🛍', label: '卖闲置', color: '#12B76A', url: '/pages/goods/publish' },
  { key: 'buddy', glyph: '🤝', label: '找搭子', color: '#7A5AF8', url: '/pages/buddy/publish' },
  { key: 'activity', glyph: '🎯', label: '发活动', color: '#F79009', url: '/pages/activity/publish' },
  { key: 'competition', glyph: '🏆', label: '发竞赛', color: '#E65100', url: '/pages/competition/publish' }
];

Page({
  data: {
    id: null,
    isEdit: false,

    content: '',
    images: [],
    topics: [],           // [{ id, name }]
    topicInput: '',
    link: null,           // { type, id, title }

    visibleScope: 0,      // 0 公开 / 1 仅关注 / 2 私密（《12》§2.2）
    commentPermission: 0, // 0 所有人 / 1 关注的人 / 2 仅自己 / 3 关闭（《12》§2.2）

    visibleOptions: [
      { value: 0, label: '公开', desc: '所有人可见' },
      { value: 1, label: '仅关注', desc: '仅关注我的人可见' },
      { value: 2, label: '私密', desc: '仅自己可见' }
    ],
    commentOptions: [
      { value: 0, label: '所有人' },
      { value: 1, label: '关注的人' },
      { value: 2, label: '仅自己' },
      { value: 3, label: '关闭评论' }
    ],

    showSetting: false,
    submitting: false,
    entries: ENTRIES
  },

  onLoad(query) {
    const id = query && query.id;
    this.setData({ id: id || null, isEdit: !!id });
    if (id) this.loadForEdit(id);
    // 草稿恢复（《10》FR-M2-10）
    if (!id) this.restoreDraft();
  },

  onUnload() {
    this.saveDraft();
  },

  onHide() {
    this.saveDraft();
  },

  /* ------------------------------------------------------------------ */
  /* 草稿                                                                */
  /* ------------------------------------------------------------------ */
  restoreDraft() {
    const d = wx.getStorageSync('draft_post');
    if (d && d.savedAt && Date.now() - d.savedAt < 7 * 24 * 3600 * 1000 && (d.content || (d.images || []).length)) {
      this.setData({
        content: d.content || '',
        images: d.images || [],
        topics: d.topics || [],
        visibleScope: d.visibleScope || 0,
        commentPermission: d.commentPermission || 0
      });
      util.toast('已恢复上次编辑的草稿');
    }
  },

  saveDraft() {
    const { content, images, topics, visibleScope, commentPermission } = this.data;
    if (!content && !images.length) return;
    wx.setStorageSync('draft_post', {
      content, images, topics, visibleScope, commentPermission, savedAt: Date.now()
    });
  },

  loadForEdit(id) {
    wx.setNavigationBarTitle({ title: '编辑动态' });
    api.post.detail(id).then((d) => {
      this.setData({
        content: d.content || '',
        images: d.images || [],
        topics: (d.topics || []).map((t) => ({ name: t })),
        visibleScope: d.visibleScope || 0,
        commentPermission: d.commentPermission || 0,
        link: d.link || null
      });
    });
  },

  /* ------------------------------------------------------------------ */
  /* 内容与图片                                                          */
  /* ------------------------------------------------------------------ */
  onContentInput(e) {
    this.setData({ content: e.detail.value });
  },

  onChooseImage() {
    const rest = MAX_IMAGES - this.data.images.length;
    if (rest <= 0) {
      util.toast(`最多上传 ${MAX_IMAGES} 张图片`);
      return;
    }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const oversize = res.tempFiles.filter(f => f.size > 10 * 1024 * 1024);
        if (oversize.length) util.toast('已过滤超过 10MB 的图片');
        const ok = res.tempFiles.filter(f => f.size <= 10 * 1024 * 1024).map(f => f.tempFilePath);
        this.setData({ images: this.data.images.concat(ok) });
      }
    });
  },

  onDeleteImage(e) {
    const i = Number(e.currentTarget.dataset.index);
    this.setData({ images: this.data.images.filter((_, idx) => idx !== i) });
  },

  onPreviewImage(e) {
    const i = Number(e.currentTarget.dataset.index);
    wx.previewImage({ urls: this.data.images, current: this.data.images[i] });
  },

  onMoveImage(e) {
    const from = Number(e.currentTarget.dataset.index);
    const to = from === 0 ? this.data.images.length - 1 : from - 1;
    const arr = this.data.images.slice();
    const [it] = arr.splice(from, 1);
    arr.splice(to, 0, it);
    this.setData({ images: arr });
  },

  /* ------------------------------------------------------------------ */
  /* 话题                                                                */
  /* ------------------------------------------------------------------ */
  onTopicInput(e) {
    this.setData({ topicInput: e.detail.value });
  },

  onAddTopic() {
    const name = this.data.topicInput.trim().replace(/^#/, '');
    if (!name) return;
    if (this.data.topics.length >= 5) {
      util.toast('最多添加 5 个话题');
      return;
    }
    if (this.data.topics.some(t => t.name === name)) {
      util.toast('该话题已添加');
      return;
    }
    this.setData({ topics: this.data.topics.concat([{ name }]), topicInput: '' });
  },

  onRemoveTopic(e) {
    const name = e.currentTarget.dataset.name;
    this.setData({ topics: this.data.topics.filter(t => t.name !== name) });
  },

  /**
   * 关联内容 —— 《13》§3 未定义「内容关联选择器」接口（后端关联通过
   * POST /posts 的 link 字段提交，但选择来源需业务侧检索接口）。
   * 当前以明确提示占位，不臆造接口；提交时若 link 为空则不带该字段。
   */
  onPickLink() {
    if (this.data.link) {
      util.confirm('移除已关联的内容？').then((ok) => {
        if (ok) this.setData({ link: null });
      });
      return;
    }
    wx.showModal({
      title: '关联内容',
      content: '关联选择器依赖的活动/竞赛/商品检索接口尚未在《13-API接口文档》中单列定义，需契约评审后启用。当前可先发布，稍后编辑补充关联。',
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#1E6FE8'
    });
  },

  /* ------------------------------------------------------------------ */
  /* 设置                                                                */
  /* ------------------------------------------------------------------ */
  onOpenSetting() {
    this.setData({ showSetting: true });
  },

  onCloseSetting() {
    this.setData({ showSetting: false });
  },

  onPickVisibility(e) {
    this.setData({ visibleScope: Number(e.currentTarget.dataset.value) });
  },

  onPickComment(e) {
    this.setData({ commentPermission: Number(e.currentTarget.dataset.value) });
  },

  /* ------------------------------------------------------------------ */
  /* 切换发布类型                                                        */
  /* ------------------------------------------------------------------ */
  onSwitchEntry(e) {
    const key = e.currentTarget.dataset.key;
    const entry = ENTRIES.find(en => en.key === key);
    if (!entry || !entry.url) return;
    // 切换前保存动态草稿
    this.saveDraft();
    wx.navigateTo({ url: entry.url });
  },

  /* ------------------------------------------------------------------ */
  /* 提交                                                                */
  /* ------------------------------------------------------------------ */
  onSubmit() {
    if (this.data.submitting) return;
    if (!auth.requireAuth({ title: '发布动态需要先登录' })) return;

    const content = this.data.content.trim();
    if (!content && !this.data.images.length) {
      util.toast('请输入内容或上传图片');
      return;
    }
    if (content.length > MAX_CONTENT) {
      util.toast(`内容不能超过 ${MAX_CONTENT} 字`);
      return;
    }

    this.setData({ submitting: true });
    util.toast('发布中', 'loading', 3000);

    this.uploadImages().then((urls) => {
      const payload = {
        content: content,
        images: urls,
        topics: this.data.topics.map(t => t.name),
        visibleScope: this.data.visibleScope,
        commentPermission: this.data.commentPermission
      };
      if (this.data.link) payload.link = this.data.link;
      return this.data.isEdit
        ? api.post.update(this.data.id, payload)
        : api.post.create(payload);
    }).then((res) => {
      this.setData({ submitting: false });
      wx.hideToast();
      // 发布成功清本地草稿（同时尝试清服务端草稿，失败静默）
      wx.removeStorageSync('draft_post');
      api.draft.remove('post').catch(() => {});
      util.toast(this.data.isEdit ? '修改成功' : '发布成功', 'success');
      setTimeout(() => {
        if (this.data.isEdit) {
          wx.navigateBack();
        } else {
          wx.switchTab({ url: '/pages/feed/index' });
        }
      }, 800);
    }).catch((err) => {
      this.setData({ submitting: false });
      wx.hideToast();
      // 敏感词拦截（40004）
      if (err && err.code === 40004) {
        wx.showModal({
          title: '内容包含敏感词',
          content: '你发布的内容包含违规词汇，请修改后重新提交。',
          showCancel: false,
          confirmColor: '#1E6FE8'
        });
      } else if (err && err.code === 40302) {
        auth.requireVerified({ title: '发布动态' });
      }
    });
  },

  uploadImages() {
    const local = this.data.images.filter(p => !/^https?:/.test(p));
    const keep = this.data.images.filter(p => /^https?:/.test(p));
    if (!local.length) return Promise.resolve(this.data.images);
    util.toast('上传图片中', 'loading', 5000);
    return Promise.all(local.map(p => api.file.upload(p, C.FILE_BIZ.POST)))
      .then((results) => {
        const remote = results.map(r => (r && (r.url || (r.data && r.data.url))) || r);
        const all = keep.concat(remote);
        this.setData({ images: all });
        return all;
      });
  },

  onCancel() {
    const hasContent = this.data.content || this.data.images.length;
    if (!hasContent) {
      wx.navigateBack();
      return;
    }
    util.confirm('保存草稿并退出？', '退出编辑', '保存草稿').then((ok) => {
      if (ok) this.saveDraft();
      wx.navigateBack();
    });
  },

  noop() {}
});
