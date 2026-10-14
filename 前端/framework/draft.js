/**
 * framework/draft.js —— 五类发布页统一草稿层（FR-M2-10 / T2.6）
 *
 * 来源：
 *   《10》FR-M2-10 草稿自动保存：动态/商品/活动/竞赛/搭子五类发布页统一接入，按类型独立存储
 *   《13》§3.7 GET/PUT/DELETE /drafts/{type}（唯一键 uk_draft(user_id, draft_type)）
 *   《11》§3.5 前端节流 2s
 *
 * 分层口径（与"搜索历史"同策略，已确认不改《12》表结构）：
 *   1. 本地（store.draft，key = campus_draft_<type>）**立即写**，保证离线/秒开、不丢内容；
 *   2. 服务端（PUT /drafts/{type}）**节流 2s 同步**，实现跨设备一致；
 *   3. 读取时本地优先，本地没有才回源服务端并回填本地；
 *   4. 服务端不可用一律静默失败（silent），绝不让草稿影响发布主流程。
 *
 * 用法（五个发布页统一）：
 *   const drafts = require('../../framework/draft');
 *   drafts.save(drafts.TYPE.ACTIVITY, { form: f });
 *   const d = drafts.load(drafts.TYPE.ACTIVITY);   // → { form: {...} } | null
 *   drafts.clear(drafts.TYPE.ACTIVITY);
 */

const store = require('./storage');
const api = require('./api');
const { DRAFT_THROTTLE } = require('./config');

/** 草稿类型（《12》§2.7 draft.draft_type） */
const TYPE = {
  POST: 1,          // 动态
  GOODS: 2,         // 商品
  ACTIVITY: 3,      // 活动
  COMPETITION: 4,   // 竞赛
  BUDDY: 5          // 搭子
};

/** 服务端同步定时器（按类型各一个） */
const timers = {};

/**
 * 本地读取草稿（同步，页面 onLoad 首屏用）。
 * @returns {object|null} payload 本体（如 { form: {...} }）
 */
function load(type) {
  const item = store.draft.getLocal(type);
  return item && item.payload ? item.payload : null;
}

/**
 * 读取草稿（本地没有时回源服务端，返回 Promise）。
 */
function loadAsync(type) {
  const local = load(type);
  if (local) return Promise.resolve(local);
  return api.draft.get(type)
    .then((res) => {
      const payload = res && (res.payload !== undefined ? res.payload : res);
      if (payload) store.draft.saveLocal(type, payload);   // 回填本地，下次秒开
      return payload || null;
    })
    .catch(() => null);
}

/**
 * 保存草稿。
 * @param {number} type 草稿类型（TYPE.*）
 * @param {object} payload 任意可序列化对象（约定 { form: {...} }）
 * @param {boolean} [immediate] true=立即同步服务端（如 onUnload / 手动存草稿）
 */
function save(type, payload, immediate) {
  store.draft.saveLocal(type, payload);

  const push = () => api.draft.save(type, payload).catch(() => {});

  if (timers[type]) {
    clearTimeout(timers[type]);
    timers[type] = null;
  }
  if (immediate) {
    push();
    return;
  }
  timers[type] = setTimeout(() => {
    timers[type] = null;
    push();
  }, DRAFT_THROTTLE);
}

/** 清除草稿（发布成功后调用） */
function clear(type) {
  if (timers[type]) {
    clearTimeout(timers[type]);
    timers[type] = null;
  }
  store.draft.removeLocal(type);
  api.draft.remove(type).catch(() => {});
}

module.exports = { TYPE, load, loadAsync, save, clear };
