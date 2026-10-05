/**
 * framework/auth.js —— 权限前端层（体验层）
 *
 * 来源：《11-系统概要设计》§3.1 权限模型 /《A-前端-首页与全局框架》§3.2
 * 铁律：前端拦截只是体验层优化，**安全由服务端保证**（服务端双重校验）
 *
 * 角色态定义（《10》§1.2 术语 + §3.1 角色能力矩阵）：
 *   0 游客      —— 仅浏览，任何写操作弹登录引导
 *   1 普通用户   —— 可发布动态/商品/搭子、报名、申请组队、收藏点赞评论私信
 *   2 认证用户   —— 额外可发布活动/竞赛并管理报名
 */

const store = require('./storage');

/** 角色常量 */
const ROLE = {
  GUEST: 0,
  USER: 1,
  VERIFIED: 2
};

/** 认证四态（《10》FR-M1-06） */
const VERIFY_STATUS = {
  NONE: 0,       // 未认证
  VERIFYING: 1,  // 认证中
  PASSED: 2,     // 已认证
  REJECTED: 3    // 未通过
};

/**
 * 取当前用户，未登录返回 null
 */
function currentUser() {
  return store.user.get();
}

/**
 * 当前角色态
 * - 无用户信息 → GUEST
 * - userType=2 → VERIFIED
 * - 其余 → USER
 */
function role() {
  const u = currentUser();
  if (!u) return ROLE.GUEST;
  if (u.userType === 2) return ROLE.VERIFIED;
  return ROLE.USER;
}

/** 是否已登录（有 Token） */
function isLogin() {
  const { accessToken } = store.token.get();
  return !!accessToken && !!currentUser();
}

/** 是否认证用户 */
function isVerified() {
  return role() === ROLE.VERIFIED;
}

/** 认证状态（四态） */
function verifyStatus() {
  const u = currentUser();
  return u ? (u.verifyStatus || 0) : 0;
}

/**
 * 需要登录的操作守卫（对应《13》错误码 40001 的前端侧处理）
 * @param {object} options
 * @param {string} options.title 登录引导文案
 * @param {string} options.redirect 登录成功后返回的原页面路径（默认当前页）
 * @returns {boolean} true=已登录可继续；false=已拉起登录引导
 */
function requireAuth(options = {}) {
  if (isLogin()) return true;
  const pages = getCurrentPages();
  const cur = pages.length ? pages[pages.length - 1] : null;
  const redirect = options.redirect || (cur ? buildRedirect(cur) : '');
  const url = `/pages/auth/login?redirect=${encodeURIComponent(redirect)}` +
    (options.title ? `&title=${encodeURIComponent(options.title)}` : '');
  wx.navigateTo({ url });
  return false;
}

/**
 * 需要「认证用户」的操作守卫（《10》§3.1：发布活动/竞赛）
 * @returns {boolean}
 */
function requireVerified(options = {}) {
  if (!requireAuth(options)) return false;
  if (isVerified()) return true;
  const st = verifyStatus();
  if (st === VERIFY_STATUS.VERIFYING) {
    wx.showToast({ title: '认证审核中，请耐心等待', icon: 'none' });
    return false;
  }
  wx.showModal({
    title: '需要学号认证',
    content: '发布活动与竞赛需要先完成学号认证，是否现在去认证？',
    confirmText: '去认证',
    confirmColor: '#1E6FE8',
    success(res) {
      if (res.confirm) wx.navigateTo({ url: '/pages/auth/verify' });
    }
  });
  return false;
}

/**
 * 将页面对象序列化为可回跳路径（用于「登录后返回原页原位」TC-M1-02）
 */
function buildRedirect(page) {
  if (!page) return '/pages/home/index';
  const route = page.route || page.__route__ || '';
  const options = page.options || {};
  const qs = Object.keys(options)
    .map(k => `${k}=${encodeURIComponent(options[k])}`)
    .join('&');
  return '/' + route + (qs ? `?${qs}` : '');
}

/**
 * 角色中文名
 */
function roleText() {
  const r = role();
  if (r === ROLE.VERIFIED) return '认证用户';
  if (r === ROLE.USER) return '普通用户';
  return '游客';
}

module.exports = {
  ROLE,
  VERIFY_STATUS,
  currentUser,
  role,
  roleText,
  isLogin,
  isVerified,
  verifyStatus,
  requireAuth,
  requireVerified,
  buildRedirect
};
