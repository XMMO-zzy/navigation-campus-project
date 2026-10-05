/**
 * pages/buddy/detail —— 搭子帖详情
 *
 * 来源：
 *   《10》FR-M6-02 搭子详情 / FR-M6-04 申请加入 → 对方同意后解锁联系方式
 *   《13》§3.6 GET /buddy-posts/{id}、POST /buddy-posts/{id}/apply、
 *              POST/DELETE /buddy-posts/{id}/favorite
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const C = require('../../framework/constants');

/** 申请状态的按钮短文案 */
const SHORT_HINT = { 0: '审核中', 1: '已通过', 2: '已拒绝', 3: '已撤回' };

Page({
  data: {
    id: null,
    loading: true,
    error: false,
    post: null,
    isOwner: false,
    applied: false,
    appliedStatus: null,   // 0 待处理 1 已通过 2 已拒绝
    statusHintText: '审核中',
    contactUnlocked: false,
    favorited: false,
    showApply: false,
    applyMessage: '',
    applying: false
  },

  onLoad(query) {
    const id = query && query.id;
    if (!id) { this.setData({ loading: false, error: true }); return; }
    this.setData({ id });
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh()).catch(() => wx.stopPullDownRefresh());
  },

  load() {
    return api.buddy.detail(this.data.id).then((d) => {
      const me = auth.currentUser() || {};
      const post = Object.assign({}, d, {
        typeText: C.labelOf(C.BUDDY_TYPE, d.buddyType || d.type, ''),
        feeText: C.labelOf(C.BUDDY_FEE_MODE, d.feeMode, 'AA 制'),
        timeText: util.relativeTime(d.createdAt),
        genderText: d.genderLimit === 1 ? '限女生' : (d.genderLimit === 2 ? '限男生' : '不限性别'),
        joined: d.currentCount || d.joinedCount || 1,
        quota: d.quota || d.memberLimit || 2,
        images: d.images || (d.image ? [d.image] : [])
      });
      this.setData({
        loading: false,
        error: false,
        post,
        isOwner: !!(me.userId && String(d.userId) === String(me.userId)),
        applied: !!d.applied,
        appliedStatus: d.appliedStatus !== undefined ? d.appliedStatus : null,
        statusHintText: SHORT_HINT[d.appliedStatus] || '审核中',
        contactUnlocked: !!d.contactUnlocked || !!d.contact,
        favorited: !!d.favorited
      });
    }).catch(() => this.setData({ loading: false, error: true }));
  },

  onRetry() { this.setData({ loading: true, error: false }); this.load(); },

  /* ------------------------------------------------------------------ */
  onTapUser() {
    const uid = this.data.post && (this.data.post.userId || (this.data.post.user && this.data.post.user.id));
    if (!uid) return;
    wx.navigateTo({ url: `/pages/me/profile-edit?viewUserId=${uid}` });
  },

  onPreviewImage(e) {
    const i = Number(e.currentTarget.dataset.index) || 0;
    const urls = (this.data.post && this.data.post.images) || [];
    if (!urls.length) return;
    wx.previewImage({ urls, current: urls[i] });
  },

  onToggleFavorite() {
    if (!auth.requireAuth({ title: '收藏需要先登录' })) return;
    const faved = this.data.favorited;
    this.setData({ favorited: !faved });
    const req = faved ? api.buddy.unfavorite(this.data.id) : api.buddy.favorite(this.data.id);
    req.catch(() => this.setData({ favorited: faved }));
  },

  /* ------------------------------------------------------------------ */
  /* 申请加入（FR-M6-04）                                                 */
  /* ------------------------------------------------------------------ */
  onOpenApply() {
    if (!auth.requireAuth({ title: '申请加入需要先登录' })) return;
    if (this.data.isOwner) { util.toast('这是你发布的搭子帖'); return; }
    if (this.data.applied) {
      util.toast(this.statusHint());
      return;
    }
    if (this.data.post.status === 2 || this.data.post.joined >= this.data.post.quota) {
      util.toast('该搭子已满员或已结束');
      return;
    }
    this.setData({ showApply: true, applyMessage: '' });
  },

  onCloseApply() { this.setData({ showApply: false }); },

  onApplyInput(e) { this.setData({ applyMessage: e.detail.value }); },

  onSubmitApply() {
    if (this.data.applying) return;
    const msg = this.data.applyMessage.trim();
    if (msg.length < 5) { util.toast('请至少填写 5 个字，让对方更了解你'); return; }
    if (msg.length > 200) { util.toast('申请说明不能超过 200 字'); return; }

    this.setData({ applying: true });
    api.buddy.apply(this.data.id, { message: msg }).then(() => {
      this.setData({ applying: false, showApply: false, applied: true, appliedStatus: 0 });
      util.toast('申请已发送，等待对方确认', 'success');
    }).catch((err) => {
      this.setData({ applying: false });
      if (err && err.code === 40006) {
        this.setData({ applied: true, showApply: false });
        util.toast('你已提交过申请');
      }
    });
  },

  statusHint() {
    const s = this.data.appliedStatus;
    if (s === 1) return '申请已通过，可查看联系方式';
    if (s === 2) return '申请已被拒绝';
    if (s === 3) return '申请已撤回';
    return '申请审核中，请耐心等待';
  },

  onCopyContact() {
    const c = this.data.post && this.data.post.contact;
    if (!c) { util.toast('对方暂未提供联系方式'); return; }
    util.copy(c);
  },

  onManage() {
    const self = this;
    wx.showActionSheet({
      itemList: ['编辑搭子帖', '关闭搭子帖', '删除搭子帖'],
      success(res) {
        if (res.tapIndex === 0) {
          wx.navigateTo({ url: `/pages/buddy/publish?id=${self.data.id}` });
        } else if (res.tapIndex === 1) {
          util.confirm('关闭后不再接受新的申请，确定关闭？').then(ok => {
            if (!ok) return;
            api.buddy.detail(self.data.id); // 说明：关闭动作复用 update 契约
            util.toast('请在编辑页调整状态后保存');
          });
        } else if (res.tapIndex === 2) {
          util.confirm('删除后不可恢复，确定删除？', '删除搭子帖', '删除').then(ok => {
            if (!ok) return;
            util.toast('删除接口待契约确认');
          });
        }
      },
      fail: () => {}
    });
  },

  onReport() {
    wx.navigateTo({ url: `/pages/me/reports?action=create&targetType=5&targetId=${this.data.id}` });
  },

  onShareAppMessage() {
    const p = this.data.post || {};
    return {
      title: p.title || '校园搭子',
      path: `/pages/buddy/detail?id=${this.data.id}`,
      imageUrl: (p.images && p.images[0]) || ''
    };
  },

  noop() {}
});
