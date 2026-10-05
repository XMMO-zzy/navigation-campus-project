/**
 * framework/api.js —— 接口定义层（唯一出口，全员消费）
 *
 * 来源：《13-API接口文档》§3 接口清单（逐条对齐，禁止臆造）
 * 规则：
 *   1. 路径与 §3 表中「方法 路径」完全一致（含 /api/v1 由 config.BASE_URL 承载）
 *   2. 每个方法注释标注对应的 FR / 文档条目
 *   3. 预留位：文档未定义但业务需要的接口，统一在本文件「§9 预留接口」区块，
 *      默认不启用（调用会抛 NOT_FOUND），待契约评审后再放行
 */

const http = require('./request');

/* ==========================================================================
   §1 M1 账号与认证（BE-D）——《13》§3.1
   ========================================================================== */
const auth = {
  /** 微信一键授权登录（code 换 openID，首登建号）| FR-M1-01 */
  wxLogin: (code, clientId) => http.post('/auth/wx-login', { code, clientId }),

  /** 手机号验证码登录（备用通道）| FR-M1-02 */
  phoneLogin: (phone, smsCode, clientId) =>
    http.post('/auth/phone-login', { phone, smsCode, clientId }),

  /** 刷新 accessToken | FR-M1-04（一般由 request 层自动调用） */
  refresh: (refreshToken) => http.post('/auth/refresh', { refreshToken }, { silentAuth: true }),

  /** 退出登录（清除服务端会话）| FR-M1-07 */
  logout: () => http.post('/users/logout', {})
};

const user = {
  /** 我的主页头部：资料卡 + 数据概览 + 认证状态 | FR-M7-07 */
  me: () => http.get('/users/me'),

  /** 编辑资料（头像/昵称/简介/专业/技能/爱好）| FR-M7-07 */
  updateMe: (data) => http.put('/users/me', data),

  /** 他人主页（受隐私设置约束）| FR-M2-11 */
  detail: (id) => http.get(`/users/${id}`),

  /** 账号注销（冷静期 15 天）| FR-M1-08 */
  deactivate: (data) => http.post('/users/deactivate', data),

  /** 屏蔽 / 取消屏蔽 | FR-M2-11 */
  block: (id) => http.post(`/users/${id}/block`, {}),
  unblock: (id) => http.del(`/users/${id}/block`),

  /** 隐私设置（资料/动态可见范围、联系权限、个性化推荐开关）| FR-M7-06 */
  getPrivacy: () => http.get('/users/me/privacy'),
  updatePrivacy: (data) => http.put('/users/me/privacy', data),

  /** 黑名单管理 | FR-M7-06 */
  getBlacklist: () => http.get('/users/me/blacklist'),
  updateBlacklist: (data) => http.put('/users/me/blacklist', data)
};

const verify = {
  /** 提交学号认证（学号+姓名+校园卡照片）| FR-M1-05 */
  apply: (data) => http.post('/verify/apply', data),

  /** 认证四态 + 未通过原因 | FR-M1-06 */
  status: () => http.get('/verify/status')
};

/* ==========================================================================
   §2 M2 内容与信息流（BE-E / 前端 FE-B）——《13》§3.2
   ========================================================================== */
const feed = {
  /**
   * 动态页信息流
   * @param {'recommend'|'follow'|'activity'|'campus'} tab
   * | FR-M2-01/02
   */
  list: (tab, page = 1, size = 20, extra = {}) =>
    http.get('/feed', http.pageParams(Object.assign({ tab }, extra), page, size)),

  /** 首页推荐流（机会推荐 + 信息推荐）| FR-M2-08/09 */
  home: (page = 1, size = 20, extra = {}) =>
    http.get('/home/feeds', http.pageParams(extra, page, size)),

  /** 首页重要消息待办（即将截止/报名开始/规则更新/协作待办）| FR-M3-05 */
  todos: () => http.get('/todos')
};

const post = {
  /** 发布动态（走敏感词机审）| FR-M2-04 */
  create: (data) => http.post('/posts', data),

  /** 动态详情 | FR-M2-02 */
  detail: (id) => http.get(`/posts/${id}`),

  /** 编辑 / 删除（仅本人，删为软删）| FR-M7-07 */
  update: (id, data) => http.put(`/posts/${id}`, data),
  remove: (id) => http.del(`/posts/${id}`),

  /** 点赞 / 取消 | FR-M2-03 */
  like: (id) => http.post(`/posts/${id}/like`, {}),
  unlike: (id) => http.del(`/posts/${id}/like`),

  /** 收藏 / 取消（触发截止提醒联动）| FR-M2-03 */
  favorite: (id) => http.post(`/posts/${id}/favorite`, {}),
  unfavorite: (id) => http.del(`/posts/${id}/favorite`),

  /** 评论列表 / 发表评论（校验评论权限）| FR-M2-03 */
  comments: (id, page = 1, size = 20) =>
    http.get(`/posts/${id}/comments`, http.pageParams({}, page, size)),
  comment: (id, data) => http.post(`/posts/${id}/comments`, data),

  /** 不感兴趣（负反馈入画像）| FR-M2-11 */
  dislike: (id) => http.put(`/posts/${id}/dislike`, {})
};

const search = {
  /**
   * 全局搜索（动态/帖子/重要消息/校园话题）
   * @param {string} kw
   * @param {''|'post'|'topic'|'notice'|'message'} type
   * | FR-M2-07
   */
  query: (kw, type = '', page = 1, size = 20) =>
    http.get('/search', http.pageParams({ kw, type }, page, size)),

  /** 搜索历史（DELETE 支持单条 ?id= 与全部清空）| FR-M2-07 */
  history: () => http.get('/search/history'),
  removeHistory: (id) => http.del('/search/history', id ? { id } : {}),
  clearHistory: () => http.del('/search/history'),

  /** 热门搜索词（Redis ZSet）| FR-M2-07 */
  hot: () => http.get('/search/hot')
};

const notice = {
  /** 官方通知/规则更新/平台公告列表 | FR-M2-09 */
  list: (page = 1, size = 20, type) =>
    http.get('/notices', http.pageParams(type ? { type } : {}, page, size)),
  detail: (id) => http.get(`/notices/${id}`)
};

/* ==========================================================================
   §3 M3 消息与协作（BE-F / 前端 FE-C）——《13》§3.3
   ========================================================================== */
const message = {
  /**
   * 通知列表
   * @param {'all'|'unread'} box
   * @param {''|'activity'|'competition'|'trade'|'buddy'|'system'} biz
   */
  list: (box = 'all', biz = '', page = 1, size = 20) =>
    http.get('/messages', http.pageParams({ box, biz }, page, size)),

  /** 全部已读 | FR-M3-02 */
  readAll: () => http.put('/messages/read-all', {}),

  /** 单条已读 */
  read: (id) => http.put(`/messages/${id}/read`, {}),

  /** 各分类未读数（Tab 角标聚合）| FR-M3-08 */
  badge: () => http.get('/messages/badge', {}, { silent: true }),

  /** 消息分类开关 + 免打扰设置 | FR-M3-02 */
  getSettings: () => http.get('/messages/settings'),
  updateSettings: (data) => http.put('/messages/settings', data)
};

const im = {
  /** 获取 TIM userSig（私信/群聊通道）| FR-M3-03 */
  sign: () => http.get('/im/sign')
};

const team = {
  /**
   * 申请中心
   * @param {'received'|'sent'|'invited'} direction
   * | FR-M3-04
   */
  applies: (direction, page = 1, size = 20) =>
    http.get('/team-applies', http.pageParams({ direction }, page, size)),

  /** 处理：agree / reject / ask（补充询问）/ withdraw（撤回）| FR-M3-04 */
  handleApply: (id, action, message) =>
    http.put(`/team-applies/${id}`, { action, message }),

  /** 创建队伍（活动/竞赛组队）| US-03a */
  create: (data) => http.post('/teams', data),

  /** 队伍详情（成员/分工/公告/进度）| FR-M3-04 */
  detail: (id) => http.get(`/teams/${id}`),

  /** 移除成员 / 转让队长（组织者权限）| FR-M5-01 */
  updateMember: (teamId, uid, data) => http.put(`/teams/${teamId}/members/${uid}`, data),

  /** 申请加入队伍 */
  apply: (teamId, data) => http.post(`/teams/${teamId}/apply`, data)
};

/* ==========================================================================
   §4 M4 二手交易（BE-G）——《13》§3.4
   ========================================================================== */
const goods = {
  /** 发布商品（3 步向导数据一次提交；含图片审核结果）| FR-M4-01 */
  create: (data) => http.post('/goods', data),

  /**
   * 商品列表（两栏瀑布流 + 筛选）
   * @param {number} category 品类
   * @param {number} cond 成色
   * @param {number} min 最低价
   * @param {number} max 最高价
   * | FR-M4-02
   */
  list: (params = {}, page = 1, size = 20) =>
    http.get('/goods', http.pageParams(params, page, size)),

  /** 我的视图：published / favorite / orders | FR-M4-03 */
  my: (view, page = 1, size = 20) =>
    http.get('/goods/my', http.pageParams({ view }, page, size)),

  /** 商品详情（联系方式按权限脱敏）| FR-M4-02 */
  detail: (id) => http.get(`/goods/${id}`),

  /** 编辑 / 下架 */
  update: (id, data) => http.put(`/goods/${id}`, data),
  remove: (id) => http.del(`/goods/${id}`),

  /** 收藏 / 取消 */
  favorite: (id) => http.post(`/goods/${id}/favorite`, {}),
  unfavorite: (id) => http.del(`/goods/${id}/favorite`),

  /** 发布求购（触发反向推送）| FR-M4-06 */
  createWanted: (data) => http.post('/goods-wanted', data)
};

const order = {
  /** 下单（买家视角创建，待卖家确认）| FR-M4-04 */
  create: (goodsId) => http.post('/orders', { goodsId }),

  /** 卖家确认（同意交易）| FR-M4-04 */
  sellerConfirm: (no) => http.post(`/orders/${no}/seller-confirm`, {}),

  /** 面交双向确认（买卖双方各调一次）| FR-M4-04 */
  faceConfirm: (no) => http.post(`/orders/${no}/face-confirm`, {}),

  /** 取消订单（状态机校验）| FR-M4-05 */
  cancel: (no, reason) => http.post(`/orders/${no}/cancel`, { cancelReason: reason }),

  /** 订单详情 + 状态时间线 | FR-M4-04 */
  detail: (no) => http.get(`/orders/${no}`),

  /** 我的订单列表（买家/卖家双视角）| FR-M4-03 */
  list: (role = 'buyer', status, page = 1, size = 20) =>
    http.get('/orders', http.pageParams({ role, status }, page, size)),

  /** 互评 + 信用分变更（P2）| FR-M4-07 */
  review: (no, data) => http.post(`/orders/${no}/review`, data)
};

/* ==========================================================================
   §5 M5 活动与竞赛（BE-G）——《13》§3.5
   ========================================================================== */
const activity = {
  /** 发布活动（仅认证用户，服务端校验）| FR-M5-01 */
  create: (data) => http.post('/activities', data),

  /** 活动列表 | FR-M5-02 */
  list: (params = {}, page = 1, size = 20) =>
    http.get('/activities', http.pageParams(params, page, size)),

  /** 活动详情（时间/人数/地点/要求/规则）| FR-M5-02 */
  detail: (id) => http.get(`/activities/${id}`),

  /** 报名（重复拦截 40006、截止拦截 40007）| FR-M5-05 */
  enroll: (id, data) => http.post(`/activities/${id}/enroll`, data || {}),

  /** 组织者审核报名：approve / reject | FR-M5-02 */
  reviewEnroll: (enrollId, action, reason) =>
    http.put(`/enrolls/${enrollId}`, { action, reason }),

  /** 我参与的活动（进行中/历史）| FR-M7-07 */
  myEnrolls: (status) => http.get('/activities/my-enrolls', status ? { status } : {}),

  /** 我发布的活动 | FR-M7-07 */
  myPublished: (page = 1, size = 20) =>
    http.get('/activities/my', http.pageParams({}, page, size))
};

const competition = {
  /** 发布竞赛（仅认证用户）| FR-M5-03 */
  create: (data) => http.post('/competitions', data),

  /** 竞赛列表 + 多维筛选 | FR-M5-04 */
  list: (params = {}, page = 1, size = 20) =>
    http.get('/competitions', http.pageParams(params, page, size)),

  /** 竞赛详情 */
  detail: (id) => http.get(`/competitions/${id}`),

  /** 竞赛日程日历（报名/初赛/决赛/截止）| FR-M5-04 */
  calendar: (month) => http.get('/competitions/calendar', { month }),

  /** 报名竞赛（可关联 team_id）| FR-M5-05 */
  enroll: (id, data) => http.post(`/competitions/${id}/enroll`, data || {}),

  /** 提交成果（获奖/作品资料，P2）| FR-M5-07 */
  submitResult: (enrollId, data) => http.put(`/competition-enrolls/${enrollId}/result`, data),

  /** 我的参赛记录 + 成果展示 | FR-M7-07 */
  myEnrolls: (status) => http.get('/competitions/my-enrolls', status ? { status } : {}),

  /** 我发布的竞赛 */
  myPublished: (page = 1, size = 20) =>
    http.get('/competitions/my', http.pageParams({}, page, size))
};

/* ==========================================================================
   §6 M6 找搭子（BE-G）——《13》§3.6
   ========================================================================== */
const buddy = {
  /** 发布搭子帖（类型/标签/展示信息/gender_limit）| FR-M6-01 */
  create: (data) => http.post('/buddy-posts', data),

  /** 搭子列表（爱好筛选、女性模式）| FR-M6-02/05 */
  list: (params = {}, page = 1, size = 20) =>
    http.get('/buddy-posts', http.pageParams(params, page, size)),

  /** 搭子帖详情 */
  detail: (id) => http.get(`/buddy-posts/${id}`),

  /** 我的视图：published / favorite / matched（我滴过的）| FR-M6-03 */
  my: (view, page = 1, size = 20) =>
    http.get('/buddy-posts/my', http.pageParams({ view }, page, size)),

  /** 申请加入 → 对方同意后解锁联系方式 | FR-M6-04 */
  apply: (id, data) => http.post(`/buddy-posts/${id}/apply`, data || {}),

  /** 收藏 / 取消 */
  favorite: (id) => http.post(`/buddy-posts/${id}/favorite`, {}),
  unfavorite: (id) => http.del(`/buddy-posts/${id}/favorite`)
};

/* ==========================================================================
   §7 M7 支撑与治理（BE-D / 全员接入）——《13》§3.7
   ========================================================================== */
const file = {
  /** 文件上传（返回 file_id/url + 审核状态，≤10MB，白名单）| FR-M7-04 */
  upload: (filePath, bizType) => http.upload(filePath, bizType)
};

const draft = {
  /** 草稿读取 | FR-M2-10 */
  get: (type) => http.get(`/drafts/${type}`, null, { silent: true }),

  /** 草稿保存（五类，JSON payload）| FR-M2-10 */
  save: (type, payload) => http.put(`/drafts/${type}`, { payload }, { silent: true }),

  /** 草稿删除 */
  remove: (type) => http.del(`/drafts/${type}`, null, { silent: true })
};

const report = {
  /** 举报（原因 + 证据材料）| FR-M7-03 */
  create: (data) => http.post('/reports', data),

  /** 我的举报记录（进度/结果/申诉入口）| FR-M7-06 */
  my: (page = 1, size = 20) => http.get('/reports/my', http.pageParams({}, page, size)),

  /** 申诉 */
  appeal: (id, data) => http.post(`/reports/${id}/appeal`, data)
};

const config = {
  /** 前端字典（品类/成色/话题等 sys_config） */
  all: () => http.get('/configs', null, { silent: true })
};

const feedback = {
  /** 帮助与反馈 | FR-M7-06 */
  create: (data) => http.post('/feedback', data),
  list: (page = 1, size = 20) => http.get('/feedback', http.pageParams({}, page, size))
};

/* ==========================================================================
   §8 关注体系（FR-M2-01「关注」页签 / FR-M2-11「新增关注」通知）
   —— 契约缺口（阶段二已标记）：
   《13》§3 未定义关注接口。前端按文档实现「关注」页签的只读展示与空态引导，
   此处预留方法但默认不启用：调用将抛出明确错误，避免臆造接口被静默使用。
   待契约评审通过后，把 RESERVED_ENABLED 置为 true 即可放行。
   ========================================================================== */
const RESERVED_ENABLED = false;

function reserved(name) {
  if (RESERVED_ENABLED) return null;
  const err = new Error(`[预留接口] ${name} 未在《13-API接口文档》中定义，需契约评审后启用`);
  console.warn(err.message);
  throw err;
}

const follow = {
  /** 关注用户（预留） */
  follow: (userId) => { reserved('POST /users/{id}/follow'); return http.post(`/users/${userId}/follow`, {}); },
  /** 取消关注（预留） */
  unfollow: (userId) => { reserved('DELETE /users/{id}/follow'); return http.del(`/users/${userId}/follow`); },
  /** 我的关注列表（预留） */
  list: (page = 1, size = 20) => { reserved('GET /users/me/following'); return http.get('/users/me/following', http.pageParams({}, page, size)); }
};

/* ==========================================================================
   §9 统一导出
   ========================================================================== */
module.exports = {
  /**
   * 幂等键生成器（《13》§1：写操作带 clientId 防重复提交）。
   * 说明：request 层对 POST/PUT/DELETE 会自动注入 clientId；
   * 此处对外暴露，供页面在「需要显式指定」的场景（如登录、表单提交）手动调用。
   */
  uuid: http.uuid,

  auth,
  user,
  verify,
  feed,
  post,
  search,
  notice,
  message,
  im,
  team,
  goods,
  order,
  activity,
  competition,
  buddy,
  file,
  draft,
  report,
  config,
  feedback,
  follow,
  RESERVED_ENABLED
};
