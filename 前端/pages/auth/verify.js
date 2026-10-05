/**
 * pages/auth/verify —— 学号认证（四态展示 + 提交申请）
 *
 * 来源：
 *   《10》FR-M1-05 提交学号认证（学号+姓名+校园卡照片）/ FR-M1-06 认证四态
 *   《13》§3.1 POST /verify/apply、GET /verify/status
 *   《12》§2.1 user.user_type（1 普通用户 / 2 认证用户）
 *
 * 四态（VERIFY_STATUS）：0 未认证 / 1 审核中 / 2 已认证 / 3 已驳回（含原因）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');
const { VERIFY_STATUS } = require('../../framework/auth');

Page({
  data: {
    status: 0,          // 四态
    reason: '',         // 驳回原因
    userType: 0,

    /* 表单 */
    studentNo: '',
    realName: '',
    cardImage: '',      // 校园卡照片本地路径 / url

    submitting: false,
    loaded: false
  },

  onLoad() {
    this.loadStatus();
  },

  onShow() {
    if (this.data.loaded) this.loadStatus();
  },

  loadStatus() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '认证需要先登录' });
      return;
    }
    return api.verify.status().then((data) => {
      const d = data || {};
      this.setData({
        status: d.status !== undefined ? d.status : 0,
        reason: d.reason || d.rejectReason || '',
        userType: d.userType || 0,
        loaded: true
      });
    }).catch(() => this.setData({ loaded: true }));
  },

  /* ------------------------------------------------------------------ */
  /* 表单                                                                */
  /* ------------------------------------------------------------------ */
  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  onChooseCard() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const f = res.tempFiles[0];
        if (f.size > 10 * 1024 * 1024) {
          util.toast('图片不能超过 10MB');
          return;
        }
        this.setData({ cardImage: f.tempFilePath });
      }
    });
  },

  onPreviewCard() {
    if (!this.data.cardImage) return;
    wx.previewImage({ urls: [this.data.cardImage] });
  },

  /* ------------------------------------------------------------------ */
  /* 提交                                                                */
  /* ------------------------------------------------------------------ */
  onSubmit() {
    if (this.data.submitting) return;

    const no = this.data.studentNo.trim();
    const name = this.data.realName.trim();
    const card = this.data.cardImage;

    if (!name) {
      util.toast('请输入真实姓名');
      return;
    }
    if (name.length > 20) {
      util.toast('姓名过长');
      return;
    }
    if (!no) {
      util.toast('请输入学号');
      return;
    }
    if (!/^[A-Za-z0-9]{4,20}$/.test(no)) {
      util.toast('学号格式不正确（4~20 位字母或数字）');
      return;
    }
    if (!card) {
      util.toast('请上传校园卡或学生证照片');
      return;
    }

    this.setData({ submitting: true });
    util.toast('提交中', 'loading', 5000);

    this.uploadCard(card).then((url) => {
      return api.verify.apply({
        studentNo: no,
        realName: name,
        cardImage: url
      });
    }).then(() => {
      this.setData({ submitting: false, status: 1 });
      wx.hideToast();
      util.toast('提交成功，等待审核', 'success');
      setTimeout(() => this.loadStatus(), 600);
    }).catch((err) => {
      this.setData({ submitting: false });
      wx.hideToast();
      if (err && err.code === 40008) {
        util.toast('该学号已被认证，请检查后重试');
      }
    });
  },

  uploadCard(localPath) {
    if (/^https?:/.test(localPath)) return Promise.resolve(localPath);
    const { FILE_BIZ } = require('../../framework/constants');
    return api.file.upload(localPath, FILE_BIZ.VERIFY_CARD)
      .then(r => (r && (r.url || (r.data && r.data.url))) || r);
  },

  /** 重新提交（驳回后） */
  onReapply() {
    this.setData({ status: 0, cardImage: '' });
  },

  onBack() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/me/index' }) });
  },

  /** 认证权益说明（《10》§2.1 认证用户能力） */
  onTapBenefit() {
    wx.showModal({
      title: '认证后可以做什么',
      content: '1. 发布二手商品、活动、竞赛与搭子帖；\n2. 报名活动与竞赛；\n3. 发起组队与加入队伍；\n4. 使用完整互动能力（评论、私信）。',
      showCancel: false,
      confirmColor: '#1E6FE8'
    });
  }
});
