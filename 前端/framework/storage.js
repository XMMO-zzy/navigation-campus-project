/**
 * framework/storage.js —— 本地存储封装（统一前缀 + 容错）
 *
 * 来源：《11-系统概要设计》§3.5 草稿与容错 /《A》§3.1 返回保位（storage）
 */

const { STORAGE_KEYS } = require('./config');

/** 读取（带默认值，容错解析失败） */
function get(key, defaultValue = null) {
  try {
    const v = wx.getStorageSync(key);
    if (v === '' || v === null || v === undefined) return defaultValue;
    return v;
  } catch (e) {
    return defaultValue;
  }
}

/** 写入 */
function set(key, value) {
  try {
    wx.setStorageSync(key, value);
    return true;
  } catch (e) {
    return false;
  }
}

/** 删除 */
function remove(key) {
  try {
    wx.removeStorageSync(key);
    return true;
  } catch (e) {
    return false;
  }
}

/* --------------------------------------------------------------------------
   业务语义封装
   -------------------------------------------------------------------------- */

/** Token / 用户信息 */
const token = {
  get() {
    return {
      accessToken: get(STORAGE_KEYS.ACCESS_TOKEN, ''),
      refreshToken: get(STORAGE_KEYS.REFRESH_TOKEN, ''),
      expireAt: get(STORAGE_KEYS.TOKEN_EXPIRE_AT, 0)
    };
  },
  save({ accessToken, refreshToken, expiresIn }) {
    if (accessToken) set(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
    if (refreshToken) set(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
    if (expiresIn) set(STORAGE_KEYS.TOKEN_EXPIRE_AT, Date.now() + expiresIn * 1000);
  },
  clear() {
    remove(STORAGE_KEYS.ACCESS_TOKEN);
    remove(STORAGE_KEYS.REFRESH_TOKEN);
    remove(STORAGE_KEYS.TOKEN_EXPIRE_AT);
    remove(STORAGE_KEYS.USER_INFO);
  }
};

/** 用户信息 */
const user = {
  get() { return get(STORAGE_KEYS.USER_INFO, null); },
  save(u) { return set(STORAGE_KEYS.USER_INFO, u); },
  clear() { return remove(STORAGE_KEYS.USER_INFO); }
};

/** 搜索历史（FR-M2-07：单条删除 + 一键清空） */
const searchHistory = {
  list() { return get(STORAGE_KEYS.SEARCH_HISTORY, []); },
  add(kw, max = 20) {
    if (!kw || !kw.trim()) return this.list();
    const cur = this.list().filter(x => x !== kw);
    cur.unshift(kw);
    const next = cur.slice(0, max);
    set(STORAGE_KEYS.SEARCH_HISTORY, next);
    return next;
  },
  removeOne(kw) {
    const next = this.list().filter(x => x !== kw);
    set(STORAGE_KEYS.SEARCH_HISTORY, next);
    return next;
  },
  clear() { return set(STORAGE_KEYS.SEARCH_HISTORY, []); }
};

/** 列表位置与筛选条件（FR-M7-02：返回保留列表位置与筛选条件） */
const position = {
  save(pageKey, payload) {
    const all = get(STORAGE_KEYS.LIST_POSITION, {});
    all[pageKey] = { ...payload, ts: Date.now() };
    return set(STORAGE_KEYS.LIST_POSITION, all);
  },
  get(pageKey) {
    const all = get(STORAGE_KEYS.LIST_POSITION, {});
    const item = all[pageKey];
    // 30 分钟有效，避免过期状态干扰
    if (!item || Date.now() - (item.ts || 0) > 30 * 60 * 1000) return null;
    return item;
  },
  clear(pageKey) {
    const all = get(STORAGE_KEYS.LIST_POSITION, {});
    delete all[pageKey];
    return set(STORAGE_KEYS.LIST_POSITION, all);
  }
};

/** 草稿（FR-M2-10：五类发布页，节流 2s 本地 + 服务端双写） */
const draft = {
  localKey(type) { return `${STORAGE_KEYS.DRAFT_PREFIX}${type}`; },
  saveLocal(type, payload) { return set(this.localKey(type), { payload, ts: Date.now() }); },
  getLocal(type) {
    const item = get(this.localKey(type), null);
    if (!item) return null;
    // 草稿保留 7 天
    if (Date.now() - (item.ts || 0) > 7 * 24 * 3600 * 1000) {
      this.removeLocal(type);
      return null;
    }
    return item;
  },
  removeLocal(type) { return remove(this.localKey(type)); }
};

module.exports = { get, set, remove, token, user, searchHistory, position, draft };
