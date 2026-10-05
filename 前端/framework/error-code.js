/**
 * framework/error-code.js —— 全局错误码映射
 *
 * 来源：《13-API接口文档》§2 错误码（全局唯一，禁止私设）
 * 用途：请求层统一拦截 → 按 code 执行前端处理建议（Toast / 登录引导 / 认证引导 / 表单错误）
 */

/** 错误码常量（与《13》§2 表逐行一致） */
const CODE = {
  SUCCESS: 0,

  NOT_LOGIN: 40001,        // 未登录 / Token 无效 → 清除本地态，弹登录引导
  TOKEN_EXPIRED: 40002,    // Token 过期 → 静默刷新重试一次，失败转 40001
  NO_PERMISSION: 40003,    // 权限不足（需认证用户）→ 引导去认证页
  PARAM_INVALID: 40004,    // 参数校验失败 → 表单错误提示（msg 携带字段说明）
  NOT_FOUND: 40005,        // 资源不存在 → 空态/返回列表
  DUPLICATE: 40006,        // 重复操作 → Toast 提示
  STATE_CONFLICT: 40007,   // 状态冲突 → Toast + 刷新详情
  SENSITIVE_WORD: 40008,   // 内容命中敏感词 → 高亮提示修改
  RATE_LIMIT: 40009,       // 频率超限 → Toast「操作太频繁」
  FILE_INVALID: 40010,     // 文件类型/大小不合规 → 引导重传

  VERIFYING: 41001,        // 学号认证审核中 → 认证页置为「认证中」态
  VERIFY_REJECTED: 41002,  // 认证未通过 → 展示驳回原因 + 重新提交入口
  WX_API_FAIL: 42001,      // 微信授权/服务通知调用失败 → 引导重试
  SERVER_ERROR: 50000,     // 服务器内部错误
  THIRD_PARTY_FAIL: 50001  // 第三方依赖失败（IM/COS/审核）
};

/** 各错误码的默认提示文案（服务端 msg 优先，此表为兜底） */
const MSG_MAP = {
  [CODE.NOT_LOGIN]: '请先登录',
  [CODE.TOKEN_EXPIRED]: '登录已过期，请重新登录',
  [CODE.NO_PERMISSION]: '权限不足',
  [CODE.PARAM_INVALID]: '提交内容有误，请检查',
  [CODE.NOT_FOUND]: '内容不存在或已被删除',
  [CODE.DUPLICATE]: '请勿重复操作',
  [CODE.STATE_CONFLICT]: '当前状态不支持该操作',
  [CODE.SENSITIVE_WORD]: '内容包含敏感词，请修改后重试',
  [CODE.RATE_LIMIT]: '操作太频繁，请稍后再试',
  [CODE.FILE_INVALID]: '文件类型或大小不符合要求',
  [CODE.VERIFYING]: '认证审核中，请耐心等待',
  [CODE.VERIFY_REJECTED]: '认证未通过',
  [CODE.WX_API_FAIL]: '微信服务调用失败，请重试',
  [CODE.SERVER_ERROR]: '服务开小差了，请稍后重试',
  [CODE.THIRD_PARTY_FAIL]: '服务暂时不可用，请稍后重试'
};

/** 需要「清除登录态 + 弹登录引导」的错误码 */
const AUTH_CODES = [CODE.NOT_LOGIN, CODE.TOKEN_EXPIRED];

/** 需要「引导去学号认证」的错误码 */
const VERIFY_CODES = [CODE.NO_PERMISSION];

/**
 * 取错误提示文案
 * @param {number} code
 * @param {string} [serverMsg] 服务端返回的 msg（优先）
 * @returns {string}
 */
function messageOf(code, serverMsg) {
  if (serverMsg && String(serverMsg).trim()) return serverMsg;
  return MSG_MAP[code] || '操作失败，请稍后重试';
}

module.exports = { CODE, MSG_MAP, AUTH_CODES, VERIFY_CODES, messageOf };
