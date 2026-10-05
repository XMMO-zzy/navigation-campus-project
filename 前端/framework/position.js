/**
 * framework/position.js —— 列表位置与筛选条件保位（FR-M7-02）
 *
 * 来源：《11-系统概要设计》§3.5 /《A》§3.1（onUnload 存 scrollOffset + 筛选参数）
 * 用法：页面 data 中记录 scrollTop 与筛选条件，onUnload 时 savePosition，onShow 时 restore
 */

const store = require('./storage');

/** 保存（在页面 onUnload / onHide 调用） */
function save(pageKey, scrollTop, filters) {
  store.position.save(pageKey, { scrollTop: scrollTop || 0, filters: filters || {} });
}

/** 读取（在页面 onShow 调用；返回 null 表示无缓存或已过期） */
function restore(pageKey) {
  return store.position.get(pageKey);
}

/** 清除 */
function clear(pageKey) {
  store.position.clear(pageKey);
}

/**
 * 生成保位 mixin（简化页面接入）
 * 页面需实现 onGetState() → { scrollTop, filters } 与 onSetState(state)
 */
function mixin(pageKey) {
  return {
    onUnload() {
      if (typeof this.onGetState === 'function') {
        const s = this.onGetState();
        if (s) save(pageKey, s.scrollTop, s.filters);
      }
    }
  };
}

module.exports = { save, restore, clear, mixin };
