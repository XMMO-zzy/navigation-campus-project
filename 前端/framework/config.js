/**
 * framework/config.js —— 全局环境配置
 *
 * 来源：《13-API接口文档》§1 通用约定 /《19-团队分工与联调方案》§4.3 环境准备
 * 约定：Base URL 一律指向文档定义的 /api/v1 前缀；切换环境只改此处，不动业务代码
 *      （对应《A-前端-首页与全局框架》§6 DoD「Mock 与真接口切换无代码改动」）
 */

/** 当前环境：dev | test | prod */
const ENV = 'dev';

/** 各环境 Base URL（严格对齐《19》§4.3 地址约定） */
const BASE_URL_MAP = {
  // 本地联调：指向本机 Spring Boot（context-path=/api/v1）。
  // 注意：不要用 dev.campus.local 之类未配置 hosts 的假域名，否则解析失败直接「网络错误」。
  // 微信开发者工具需在「详情 → 本地设置」勾选「不校验合法域名...」才能访问 http://127.0.0.1。
  // 真机预览时改成电脑局域网 IP，如 http://192.168.1.5:8080/api/v1
  dev: 'http://127.0.0.1:8080/api/v1',
  test: 'https://test-api.campus.example.cn/api/v1',
  prod: 'https://api.campus.example.cn/api/v1'
};

module.exports = {
  ENV,
  BASE_URL: BASE_URL_MAP[ENV] || BASE_URL_MAP.dev,

  /** 接口版本前缀（《13》§1：/api/v1） */
  API_PREFIX: '',

  /** 请求超时（ms） */
  TIMEOUT: 15000,

  /** 分页默认值（《13》§1：page 从 1，size 默认 20，≤50） */
  PAGE_SIZE: 20,
  PAGE_SIZE_MAX: 50,

  /** 全局存储 key（统一前缀，避免与宿主环境冲突） */
  STORAGE_KEYS: {
    ACCESS_TOKEN: 'campus_access_token',
    REFRESH_TOKEN: 'campus_refresh_token',
    TOKEN_EXPIRE_AT: 'campus_token_expire_at',
    USER_INFO: 'campus_user_info',
    SEARCH_HISTORY: 'campus_search_history',
    LIST_POSITION: 'campus_list_position',
    DRAFT_PREFIX: 'campus_draft_',
    SETTINGS: 'campus_settings'
  },

  /** 上传限制（《13》§3.7 / FR-M7-04：≤10MB，白名单） */
  UPLOAD: {
    MAX_SIZE: 10 * 1024 * 1024,
    MAX_COUNT: 9,
    ACCEPT: ['jpg', 'jpeg', 'png', 'gif', 'webp']
  },

  /** 草稿节流间隔（《11》§3.5：前端节流 2s） */
  DRAFT_THROTTLE: 2000
};
