/**
 * pages/me/setting-privacy —— 隐私设置
 *
 * 来源：
 *   《10》FR-M7-06 隐私设置（资料/动态可见范围、联系权限、个性化推荐开关）
 *   《13》§3.1 GET/PUT /users/me/privacy
 *   《18-风险评估与隐私合规》§3 个人信息最小化与用户可控
 */

const api = require('../../framework/api');
const auth = require('../../framework/auth');
const util = require('../../framework/util');

const PROFILE_SCOPE = [
  { value: 0, label: '所有人可见' },
  { value: 1, label: '仅认证用户可见' },
  { value: 2, label: '仅关注我的人' },
  { value: 3, label: '仅自己可见' }
];

const POST_SCOPE = [
  { value: 0, label: '所有人可见' },
  { value: 1, label: '仅关注我的人' },
  { value: 2, label: '仅认证用户可见' }
];

const CONTACT_SCOPE = [
  { value: 0, label: '认证用户可联系' },
  { value: 1, label: '所有人可联系' },
  { value: 2, label: '仅我关注的人' },
  { value: 3, label: '不允许私信' }
];

Page({
  data: {
    loaded: false,
    saving: false,
    profileScopeOptions: PROFILE_SCOPE.map(o => o.label),
    postScopeOptions: POST_SCOPE.map(o => o.label),
    contactScopeOptions: CONTACT_SCOPE.map(o => o.label),
    privacy: {
      profileScope: 0,
      postScope: 0,
      contactScope: 0,
      showStudentNo: false,
      showMajor: true,
      allowRecommend: true,
      searchable: true
    },
    profileScopeText: PROFILE_SCOPE[0].label,
    postScopeText: POST_SCOPE[0].label,
    contactScopeText: CONTACT_SCOPE[0].label
  },

  onLoad() {
    if (!auth.isLogin()) {
      auth.requireAuth({ title: '隐私设置' });
      return;
    }
    this.load();
  },

  load() {
    api.user.getPrivacy().then((d) => {
      const p = Object.assign({}, this.data.privacy, d || {});
      this.setData({
        loaded: true,
        privacy: p,
        profileScopeText: C(PROFILE_SCOPE, p.profileScope),
        postScopeText: C(POST_SCOPE, p.postScope),
        contactScopeText: C(CONTACT_SCOPE, p.contactScope)
      });
    }).catch(() => this.setData({ loaded: true }));
  },

  onPickProfileScope(e) {
    const i = Number(e.detail.value);
    const hit = PROFILE_SCOPE[i];
    this.setData({ 'privacy.profileScope': hit.value, profileScopeText: hit.label }, () => this.save());
  },

  onPickPostScope(e) {
    const i = Number(e.detail.value);
    const hit = POST_SCOPE[i];
    this.setData({ 'privacy.postScope': hit.value, postScopeText: hit.label }, () => this.save());
  },

  onPickContactScope(e) {
    const i = Number(e.detail.value);
    const hit = CONTACT_SCOPE[i];
    this.setData({ 'privacy.contactScope': hit.value, contactScopeText: hit.label }, () => this.save());
  },

  onToggle(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ ['privacy.' + key]: e.detail.value }, () => this.save());
  },

  save() {
    if (this.data.saving) return;
    this.setData({ saving: true });
    api.user.updatePrivacy(this.data.privacy).then(() => {
      this.setData({ saving: false });
    }).catch(() => this.setData({ saving: false }));
  },

  onBlacklist() {
    wx.navigateTo({ url: '/pages/me/blacklist' });
  },

  onPolicy() {
    wx.navigateTo({
      url: `/pages/webview/index?url=${encodeURIComponent('https://docs.qq.com/campus/privacy')}&title=${encodeURIComponent('隐私政策')}`
    });
  },

  onNote() {
    wx.showModal({
      title: '隐私说明',
      content: '学号、联系方式等敏感信息默认不公开。隐私设置修改后即时生效，已在其他用户端展示的历史内容可能会略有延迟。',
      showCancel: false,
      confirmColor: '#1E6FE8'
    });
  }
});

function C(list, value) {
  const hit = list.find(i => i.value === value);
  return hit ? hit.label : (list[0] && list[0].label) || '';
}
