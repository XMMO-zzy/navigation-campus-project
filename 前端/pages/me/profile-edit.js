/**
 * pages/me/profile-edit —— 编辑资料 / 他人主页
 *
 * 来源：
 *   《10》FR-M7-07 我的主页（资料/专业/技能/爱好编辑）
 *   《10》FR-M2-11 他人主页（受隐私设置约束）
 *   《13》§3.1 GET /users/me、PUT /users/me、GET /users/{id}
 *
 * 模式：
 *   - 无参：编辑自己的资料
 *   - ?viewUserId=X：查看他人主页（只读，受隐私约束）
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');

const SKILL_SUGGEST = ['Java', 'Python', '前端', '后端', 'UI 设计', '产品', '算法', '数据分析', '运营', '视频剪辑'];
const HOBBY_SUGGEST = ['篮球', '羽毛球', '跑步', '摄影', '音乐', '阅读', '桌游', '旅行', '无人机'];

/** 构建「标签 → 是否选中」映射，供 WXML 使用（WXML 不支持 indexOf） */
function buildMap(suggest, selected) {
  const map = {};
  suggest.forEach((s) => { map[s] = (selected || []).indexOf(s) > -1; });
  return map;
}

Page({
  data: {
    isSelf: true,
    viewUserId: null,
    loading: true,
    error: false,
    saving: false,

    user: null,
    form: {
      nickname: '',
      avatar: '',
      bio: '',
      major: '',
      grade: '',
      skills: [],
      hobbies: []
    },
    skillSuggest: SKILL_SUGGEST,
    hobbySuggest: HOBBY_SUGGEST,
    /** WXML 不支持 indexOf，用布尔映射驱动选中态 */
    skillOn: {},
    hobbyOn: {},
    dirty: false
  },

  onLoad(query) {
    const viewUserId = query && query.viewUserId ? query.viewUserId : null;
    const me = auth.currentUser() || {};
    // 传了 viewUserId 且不是自己 → 只读模式
    const isSelf = !viewUserId || String(viewUserId) === String(me.userId || me.id);
    this.setData({ viewUserId, isSelf });
    wx.setNavigationBarTitle({ title: isSelf ? '编辑资料' : '个人主页' });
    this.load();
  },

  load() {
    const uid = this.data.isSelf ? null : this.data.viewUserId;
    const req = uid ? api.user.detail(uid) : api.user.me();
    return req.then((u) => {
      const user = u || {};
      const skills = user.skills || [];
      const hobbies = user.hobbies || [];
      this.setData({
        loading: false,
        error: false,
        user: user,
        form: {
          nickname: user.nickname || '',
          avatar: user.avatar || '',
          bio: user.bio || '',
          major: user.major || '',
          grade: user.grade || '',
          skills: skills,
          hobbies: hobbies
        },
        skillOn: buildMap(SKILL_SUGGEST, skills),
        hobbyOn: buildMap(HOBBY_SUGGEST, hobbies)
      });
    }).catch(() => this.setData({ loading: false, error: true }));
  },

  onRetry() {
    this.setData({ loading: true, error: false });
    this.load();
  },

  /* ------------------------------------------------------------------ */
  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['form.' + field]: e.detail.value, dirty: true });
  },

  onChooseAvatar() {
    if (!this.data.isSelf) return;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: (res) => {
        const path = res.tempFiles[0].tempFilePath;
        this.setData({ 'form.avatar': path, dirty: true });
      }
    });
  },

  onToggleSkill(e) {
    if (!this.data.isSelf) return;
    const v = e.currentTarget.dataset.value;
    const list = this.data.form.skills.slice();
    const i = list.indexOf(v);
    if (i > -1) list.splice(i, 1);
    else if (list.length < 10) list.push(v);
    else { util.toast('技能最多 10 个'); return; }
    this.setData({
      'form.skills': list,
      skillOn: buildMap(SKILL_SUGGEST, list),
      dirty: true
    });
  },

  onToggleHobby(e) {
    if (!this.data.isSelf) return;
    const v = e.currentTarget.dataset.value;
    const list = this.data.form.hobbies.slice();
    const i = list.indexOf(v);
    if (i > -1) list.splice(i, 1);
    else if (list.length < 10) list.push(v);
    else { util.toast('爱好最多 10 个'); return; }
    this.setData({
      'form.hobbies': list,
      hobbyOn: buildMap(HOBBY_SUGGEST, list),
      dirty: true
    });
  },

  onAddCustom(e) {
    if (!this.data.isSelf) return;
    const field = e.currentTarget.dataset.field;
    const label = field === 'skills' ? '技能' : '爱好';
    const suggest = field === 'skills' ? SKILL_SUGGEST : HOBBY_SUGGEST;
    const self = this;
    wx.showModal({
      title: `添加${label}`,
      editable: true,
      placeholderText: `请输入${label}名称`,
      success: (res) => {
        const v = (res.content || '').trim();
        if (!res.confirm || !v) return;
        const list = self.data.form[field].slice();
        if (list.indexOf(v) > -1) { util.toast('已添加过该标签'); return; }
        if (list.length >= 10) { util.toast(`${label}最多 10 个`); return; }
        list.push(v);
        const patch = { ['form.' + field]: list, dirty: true };
        patch[field === 'skills' ? 'skillOn' : 'hobbyOn'] = buildMap(suggest, list);
        self.setData(patch);
      }
    });
  },

  /* ------------------------------------------------------------------ */
  save() {
    if (!this.data.isSelf || this.data.saving) return;
    const f = this.data.form;
    if (!f.nickname.trim()) {
      util.toast('请输入昵称');
      return;
    }
    if (f.nickname.trim().length > 20) {
      util.toast('昵称不能超过 20 个字');
      return;
    }
    if (f.bio.length > 100) {
      util.toast('个人简介不能超过 100 字');
      return;
    }
    this.setData({ saving: true });
    util.toast('保存中', 'loading', 5000);

    this.uploadAvatar(f.avatar).then((avatar) => {
      return api.user.updateMe({
        nickname: f.nickname.trim(),
        avatar: avatar,
        bio: f.bio.trim(),
        major: f.major.trim(),
        grade: f.grade.trim(),
        skills: f.skills,
        hobbies: f.hobbies
      });
    }).then(() => {
      wx.hideToast();
      this.setData({ saving: false, dirty: false });
      util.toast('保存成功', 'success');
      setTimeout(() => wx.navigateBack(), 700);
    }).catch(() => {
      wx.hideToast();
      this.setData({ saving: false });
    });
  },

  uploadAvatar(path) {
    if (!path || /^https?:/.test(path)) return Promise.resolve(path);
    const { FILE_BIZ } = require('../../framework/constants');
    return api.file.upload(path, FILE_BIZ.AVATAR)
      .then(r => (r && (r.url || (r.data && r.data.url))) || r);
  },

  onSave() { this.save(); },

  onBack() {
    if (this.data.dirty && this.data.isSelf) {
      util.confirm('有未保存的修改，确定放弃？').then((ok) => {
        if (ok) wx.navigateBack();
      });
      return;
    }
    wx.navigateBack();
  },

  /* 他人主页操作 */
  onTapMessage() {
    if (!auth.requireAuth({ title: '私信需要先登录' })) return;
    wx.showModal({
      title: '私信',
      content: '私信由腾讯云 IM 提供（《13》§3.3 GET /im/sign），属 P1/P2 批次，接口已预留，待 SDK 接入后开放。',
      showCancel: false,
      confirmColor: '#1E6FE8'
    });
  },

  onBlock() {
    const uid = this.data.viewUserId;
    if (!uid) return;
    util.confirm('屏蔽后你们将互相不可见，确定屏蔽？').then((ok) => {
      if (!ok) return;
      api.user.block(uid).then(() => {
        util.toast('已屏蔽', 'success');
        setTimeout(() => wx.navigateBack(), 700);
      });
    });
  },

  onReport() {
    wx.navigateTo({ url: `/pages/me/reports?action=create&targetType=8&targetId=${this.data.viewUserId}` });
  }
});
