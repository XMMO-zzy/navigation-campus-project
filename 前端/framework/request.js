/**
 * framework/request.js —— 请求封装（全员消费，FE-A 维护）
 *
 * 来源：《13-API接口文档》§1 通用约定 + §2 错误码
 * 职责：
 *   1. 自动拼接 Base URL 与 /api/v1 前缀
 *   2. 自动携带 Authorization: Bearer <accessToken>
 *   3. 统一解析 { code, msg, data }；code!==0 时按错误码表分派处理
 *   4. 40002 Token 过期 → 静默刷新一次后重试（《13》§2 前端处理建议）
 *   5. 写操作自动注入 clientId（幂等，FR/《13》§1）
 *
 * 对外 API：request / get / post / put / del / upload
 */

const { BASE_URL, TIMEOUT, PAGE_SIZE } = require('./config');
const store = require('./storage');
const { CODE, AUTH_CODES, VERIFY_CODES, messageOf } = require('./error-code');

/* 并发刷新锁：避免多个 40002 同时触发多次刷新 */
let refreshing = false;
let refreshQueue = [];

/** 生成幂等 clientId（前端 UUID） */
function uuid() {
  return 'c-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);
}

/** 触发登录引导（由 app.js 注入，避免框架层直接依赖页面） */
let loginHandler = null;
function setLoginHandler(fn) { loginHandler = fn; }

/** 触发认证引导 */
let verifyHandler = null;
function setVerifyHandler(fn) { verifyHandler = fn; }

/** 展示 Toast（避免频繁重复） */
let lastToastAt = 0;
function toast(msg, icon = 'none') {
  const now = Date.now();
  if (now - lastToastAt < 600) return;
  lastToastAt = now;
  wx.showToast({ title: msg, icon, duration: 2000 });
}

/** 统一错误分派 */
function handleError(code, msg, options) {
  const text = messageOf(code, msg);

  if (AUTH_CODES.indexOf(code) > -1) {
    store.token.clear();
    if (options.silentAuth !== true) {
      if (typeof loginHandler === 'function') loginHandler();
      else toast(text);
    }
    return;
  }

  if (VERIFY_CODES.indexOf(code) > -1) {
    if (typeof verifyHandler === 'function') verifyHandler(text);
    else toast(text);
    return;
  }

  if (options.silent !== true) toast(text);
}

/** 单次请求（不带重试） */
function doRequest(method, url, data, header, options) {
  return new Promise((resolve, reject) => {
    const { accessToken } = store.token.get();
    const finalHeader = Object.assign(
      { 'Content-Type': 'application/json' },
      accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      header || {}
    );

    wx.request({
      url: BASE_URL + url,
      method: method.toUpperCase(),
      data: data || {},
      header: finalHeader,
      timeout: options.timeout || TIMEOUT,
      success(res) {
        // HTTP 层异常
        if (res.statusCode !== 200) {
          const err = { code: res.statusCode, msg: `网络异常(${res.statusCode})` };
          if (res.statusCode === 401) err.code = CODE.NOT_LOGIN;
          reject(err);
          return;
        }
        const body = res.data || {};
        // 契约：{ code, msg, data }
        if (typeof body.code === 'undefined') {
          // 非标准响应（如直连 Mock），原样返回
          resolve(body);
          return;
        }
        if (body.code === CODE.SUCCESS) {
          resolve(body.data);
        } else {
          reject({ code: body.code, msg: body.msg, data: body.data });
        }
      },
      fail(err) {
        reject({ code: -1, msg: '网络连接失败，请检查网络', raw: err });
      }
    });
  });
}

/** 刷新 Token（POST /auth/refresh，不经过本函数的鉴权流程，避免递归） */
function refreshToken() {
  if (refreshing) {
    return new Promise((resolve, reject) => refreshQueue.push({ resolve, reject }));
  }
  refund.refreshing = true;
  const { refreshToken: rt } = store.token.get();
  if (!rt) {
    return Promise.reject({ code: CODE.NOT_LOGIN, msg: '未登录' });
  }
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + '/auth/refresh',
      method: 'POST',
      data: { refreshToken: rt },
      header: { 'Content-Type': 'application/json' },
      success(res) {
        const body = res.data || {};
        if (body.code === CODE.SUCCESS && body.data) {
          store.token.save(body.data);
          resolve(body.data);
        } else {
          reject({ code: body.code || CODE.NOT_LOGIN, msg: body.msg });
        }
      },
      fail() {
        reject({ code: CODE.NOT_LOGIN, msg: '刷新登录态失败' });
      },
      complete() {
        refund.refreshing = false;
        const q = refreshQueue;
        refreshQueue = [];
        q.forEach(p => p.resolve());
      }
    });
  });
}
const refund = { refreshing: false };

/**
 * 主请求方法（含 40002 静默刷新重试一次）
 * @param {string} method
 * @param {string} url 以 / 开头的路径，如 '/feed'
 * @param {object} data
 * @param {object} options { header, silent, silentAuth, timeout, retried }
 */
function request(method, url, data, options = {}) {
  // 写操作自动注入 clientId（幂等）
  let payload = data;
  const m = method.toUpperCase();
  if ((m === 'POST' || m === 'PUT' || m === 'DELETE') && payload && typeof payload === 'object' && !payload.clientId) {
    payload = Object.assign({}, payload, { clientId: uuid() });
  }

  return doRequest(m, url, payload, options.header, options)
    .catch(err => {
      // Token 过期：静默刷新一次后重试
      if (err && err.code === CODE.TOKEN_EXPIRED && !options.retried) {
        return refreshToken()
          .then(() => request(method, url, data, Object.assign({}, options, { retried: true })))
          .catch(() => {
            handleError(CODE.NOT_LOGIN, '', options);
            return Promise.reject({ code: CODE.NOT_LOGIN, msg: '登录已过期' });
          });
      }
      handleError(err.code, err.msg, options);
      return Promise.reject(err);
    });
}

/** 构造分页参数（《13》§1） */
function pageParams(params = {}, page = 1, size = PAGE_SIZE) {
  return Object.assign({}, params, { page, size: Math.min(size, 50) });
}

const api = {
  request,
  get: (url, params, options) => request('GET', url + buildQuery(params), null, options),
  post: (url, data, options) => request('POST', url, data, options),
  put: (url, data, options) => request('PUT', url, data, options),
  del: (url, data, options) => request('DELETE', url + buildQuery(data), null, options),
  pageParams,
  uuid,
  setLoginHandler,
  setVerifyHandler,
  toast
};

/** GET 查询串拼接（过滤空值） */
function buildQuery(params) {
  if (!params) return '';
  const pairs = [];
  Object.keys(params).forEach(k => {
    const v = params[k];
    if (v === undefined || v === null || v === '') return;
    pairs.push(`${encodeURIComponent(k)}=${encodeURIComponent(v)}`);
  });
  return pairs.length ? `?${pairs.join('&')}` : '';
}

/**
 * 文件上传（POST /files/upload，《13》§3.7）
 * 返回 { fileId, url, auditStatus }
 */
function upload(filePath, bizType = 1, options = {}) {
  return new Promise((resolve, reject) => {
    const { accessToken } = store.token.get();
    wx.uploadFile({
      url: BASE_URL + '/files/upload',
      filePath,
      name: 'file',
      formData: { bizType: String(bizType) },
      header: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      timeout: 60000,
      success(res) {
        try {
          const body = JSON.parse(res.data || '{}');
          if (body.code === CODE.SUCCESS) resolve(body.data);
          else {
            handleError(body.code, body.msg, options);
            reject({ code: body.code, msg: body.msg });
          }
        } catch (e) {
          reject({ code: -1, msg: '上传响应解析失败' });
        }
      },
      fail() {
        const err = { code: -1, msg: '图片上传失败，请重试' };
        toast(err.msg);
        reject(err);
      }
    });
  });
}

api.upload = upload;
module.exports = api;
