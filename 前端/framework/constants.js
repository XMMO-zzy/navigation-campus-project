/**
 * framework/constants.js —— 全局枚举 / 字典（前端展示层）
 *
 * 来源（逐项对齐，禁止私自改数值）：
 *   《12-数据库设计》§1.3 通用多态字段枚举、§2 各表字段枚举
 *   《13-API接口文档》§1 feed tab、§3 各接口参数语义
 *   《04-思维导图（重构版）》主界面设计·动态（卡片类型）
 */

/* --------------------------------------------------------------------------
   1. 内容多态类型（《12》§1.3 target_type / biz_type）
   -------------------------------------------------------------------------- */
const CONTENT_TYPE = {
  POST: 1,          // 动态
  GOODS: 2,         // 商品
  ACTIVITY: 3,      // 活动
  COMPETITION: 4,   // 竞赛
  BUDDY: 5,         // 搭子帖
  COMMENT: 6,       // 评论
  WANTED: 7,        // 求购帖
  NOTICE: 8,        // 官方通知
  TOPIC: 9,         // 校园话题（搜索结果专用，无独立详情页）
  MESSAGE: 10       // 站内消息（搜索结果专用，跳消息 Tab）
};

const CONTENT_TYPE_TEXT = {
  1: '动态',
  2: '商品',
  3: '活动',
  4: '竞赛',
  5: '搭子',
  6: '评论',
  7: '求购',
  8: '通知',
  9: '话题',
  10: '消息'
};

/** 卡片 → 跳转路径映射（发布者/卡片点击统一走此表） */
function contentTypeRoute(type, id) {
  switch (type) {
    case CONTENT_TYPE.POST: return `/pages/post/detail?id=${id}`;
    case CONTENT_TYPE.GOODS: return `/pages/goods/detail?id=${id}`;
    case CONTENT_TYPE.ACTIVITY: return `/pages/activity/detail?id=${id}`;
    case CONTENT_TYPE.COMPETITION: return `/pages/competition/detail?id=${id}`;
    case CONTENT_TYPE.BUDDY: return `/pages/buddy/detail?id=${id}`;
    case CONTENT_TYPE.NOTICE: return `/pages/notice/index?id=${id}`;
    // TOPIC(9) / MESSAGE(10) 无独立详情页：前者待话题页交付，后者是 TabBar 页需 switchTab，
    // 统一由调用方（当前为搜索页）自行处理，这里返回空串
    default: return '';
  }
}

/* --------------------------------------------------------------------------
   2. 信息流页签（《13》§3.2 GET /feed?tab=）
   -------------------------------------------------------------------------- */
const FEED_TABS = [
  { key: 'recommend', label: '推荐' },
  { key: 'follow', label: '关注' },
  { key: 'activity', label: '活动与竞赛' },
  { key: 'campus', label: '校园' }
];

/* --------------------------------------------------------------------------
   3. 动态类型（《12》§2.2 post.post_type）
   -------------------------------------------------------------------------- */
const POST_TYPE = { NORMAL: 1, EXPERIENCE: 2, CHECKIN: 3, TEAM_NEED: 4 };
const POST_TYPE_TEXT = { 1: '图文', 2: '经验分享', 3: '学习打卡', 4: '组队需求' };

/* --------------------------------------------------------------------------
   4. 可见范围 / 评论权限（《12》§2.2 post.visible_scope / comment_permission）
   -------------------------------------------------------------------------- */
const VISIBLE_SCOPE = [
  { value: 0, label: '公开' },
  { value: 1, label: '仅关注的人' },
  { value: 2, label: '私密' }
];

const COMMENT_PERMISSION = [
  { value: 0, label: '所有人' },
  { value: 1, label: '关注的人' },
  { value: 2, label: '仅自己' },
  { value: 3, label: '关闭评论' }
];

/* --------------------------------------------------------------------------
   5. 消息业务分类（《12》§2.3 notify_message.biz_type）
   -------------------------------------------------------------------------- */
const BIZ_TYPE = {
  TRADE: 10,      // 交易
  TEAM: 20,       // 组队
  INTERACT: 30,   // 互动
  SYSTEM: 40,     // 系统
  EVENT: 50,      // 活动竞赛
  PLATFORM: 60    // 平台
};

/** 消息页分类入口（《04》3.1 分类入口：活动/竞赛/商品/搭子/系统） */
const MESSAGE_BIZ_TABS = [
  { key: '', label: '全部' },
  { key: 'activity', label: '活动' },
  { key: 'competition', label: '竞赛' },
  { key: 'trade', label: '商品' },
  { key: 'buddy', label: '搭子' },
  { key: 'system', label: '系统' }
];

/* --------------------------------------------------------------------------
   6. 订单状态机（《12》§2.4 goods_order.status +《11》§3.3）
   -------------------------------------------------------------------------- */
const ORDER_STATUS = {
  PENDING_SELLER: 10,   // 待卖家确认
  PENDING_PAY: 20,      // 待付款
  PENDING_FACE: 30,     // 待面交
  PENDING_CONFIRM: 40,  // 待确认（单方已确认）
  FINISHED: 50,         // 已完成
  CANCELED: 60,         // 已取消
  TIMEOUT: 70           // 超时关闭
};

const ORDER_STATUS_TEXT = {
  10: '待卖家确认',
  20: '待付款',
  30: '待面交',
  40: '待对方确认',
  50: '已完成',
  60: '已取消',
  70: '超时关闭'
};

/* 标签样式变体名（与 app.wxss 的 .tag--{variant} 对齐） */
const ORDER_STATUS_TAG = {
  10: 'warning',
  20: 'warning',
  30: 'info',
  40: 'info',
  50: 'success',
  60: 'gray',
  70: 'gray'
};

/* --------------------------------------------------------------------------
   7. 报名状态（《12》§2.5 activity_enroll.status / competition_enroll.status）
   -------------------------------------------------------------------------- */
const ENROLL_STATUS_TEXT = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已签到', 4: '已取消' };
const ENROLL_STATUS_TAG = {
  0: 'warning', 1: 'success', 2: 'danger', 3: 'info', 4: 'gray'
};

/* --------------------------------------------------------------------------
   8. 组队申请中心（《12》§2.3 team_apply.status / direction）
   -------------------------------------------------------------------------- */
const APPLY_DIRECTION = [
  { key: 'received', label: '收到的申请' },
  { key: 'sent', label: '我的申请' },
  { key: 'invited', label: '收到的邀请' }
];

const APPLY_STATUS_TEXT = { 0: '待处理', 1: '已通过', 2: '已拒绝', 3: '已撤回', 4: '补充询问' };
const APPLY_STATUS_TAG = {
  0: 'warning', 1: 'success', 2: 'danger', 3: 'gray', 4: 'info'
};

/* --------------------------------------------------------------------------
   9. 商品（《12》§2.4 goods.cond 成色 1~5）
   -------------------------------------------------------------------------- */
const GOODS_COND = [
  { value: 1, label: '全新' },
  { value: 2, label: '几乎全新' },
  { value: 3, label: '轻微使用痕迹' },
  { value: 4, label: '明显使用痕迹' },
  { value: 5, label: '有瑕疵' }
];

/** 商品品类（后台 sys_config 字典，前端兜底值；真值走 GET /configs） */
const GOODS_CATEGORY = [
  { value: 1, label: '教材书籍' },
  { value: 2, label: '电子数码' },
  { value: 3, label: '生活用品' },
  { value: 4, label: '运动户外' },
  { value: 5, label: '服饰鞋包' },
  { value: 6, label: '交通出行' },
  { value: 7, label: '其他' }
];

/* --------------------------------------------------------------------------
   10. 搭子（《12》§2.6 buddy_post.buddy_type / gender_limit）
   -------------------------------------------------------------------------- */
const BUDDY_TYPE = [
  { value: 1, label: '学习' },
  { value: 2, label: '运动' },
  { value: 3, label: '娱乐' },
  { value: 4, label: '旅行' },
  { value: 5, label: '饭搭子' },
  { value: 6, label: '技能' }
];

const BUDDY_FEE_MODE = [
  { value: 0, label: 'AA 制' },
  { value: 1, label: '请客' }
];

/* --------------------------------------------------------------------------
   11. 活动（《12》§2.5 activity.category）
   -------------------------------------------------------------------------- */
const ACTIVITY_CATEGORY = [
  { value: 1, label: '校园活动' },
  { value: 2, label: '学术讲座' },
  { value: 3, label: '学术调研' }
];

/* --------------------------------------------------------------------------
   12. 竞赛级别（《12》§2.5 competition.level）
   -------------------------------------------------------------------------- */
const COMPETITION_LEVEL = [
  { value: 1, label: '校级' },
  { value: 2, label: '省级' },
  { value: 3, label: '国家级' }
];

/** 竞赛成果（《12》§2.5 competition_enroll.result） */
const AWARD_RESULT = [
  { value: 0, label: '无' },
  { value: 1, label: '一等奖' },
  { value: 2, label: '二等奖' },
  { value: 3, label: '三等奖' },
  { value: 4, label: '优秀奖' },
  { value: 5, label: '入围' }
];

/* --------------------------------------------------------------------------
   13. 举报（《12》§2.7 report）
   -------------------------------------------------------------------------- */
const REPORT_REASON = [
  { value: 1, label: '虚假信息' },
  { value: 2, label: '诈骗' },
  { value: 3, label: '侵权' },
  { value: 4, label: '违规内容' },
  { value: 5, label: '其他' }
];

const REPORT_STATUS_TEXT = { 0: '待处理', 1: '处理中', 2: '已处理', 3: '已驳回' };

/* --------------------------------------------------------------------------
   14. 草稿类型（《12》§2.7 draft.draft_type）
   -------------------------------------------------------------------------- */
const DRAFT_TYPE = {
  POST: 1,
  GOODS: 2,
  ACTIVITY: 3,
  COMPETITION: 4,
  BUDDY: 5
};

/* --------------------------------------------------------------------------
   15. 文件业务类型（POST /files/upload 的 bizType）
   -------------------------------------------------------------------------- */
const FILE_BIZ = {
  AVATAR: 1,
  POST: 2,
  GOODS: 3,
  ACTIVITY: 4,
  COMPETITION: 5,
  BUDDY: 6,
  VERIFY_CARD: 7,
  REPORT: 8,
  FEEDBACK: 9
};

/* --------------------------------------------------------------------------
   16. 通用工具：按 value 取 label
   -------------------------------------------------------------------------- */
function labelOf(list, value, fallback = '') {
  const hit = (list || []).find(i => i.value === value);
  return hit ? hit.label : fallback;
}

module.exports = {
  CONTENT_TYPE,
  CONTENT_TYPE_TEXT,
  contentTypeRoute,
  FEED_TABS,
  POST_TYPE,
  POST_TYPE_TEXT,
  VISIBLE_SCOPE,
  COMMENT_PERMISSION,
  BIZ_TYPE,
  MESSAGE_BIZ_TABS,
  ORDER_STATUS,
  ORDER_STATUS_TEXT,
  ORDER_STATUS_TAG,
  ENROLL_STATUS_TEXT,
  ENROLL_STATUS_TAG,
  APPLY_DIRECTION,
  APPLY_STATUS_TEXT,
  APPLY_STATUS_TAG,
  GOODS_COND,
  GOODS_CATEGORY,
  BUDDY_TYPE,
  BUDDY_FEE_MODE,
  ACTIVITY_CATEGORY,
  COMPETITION_LEVEL,
  AWARD_RESULT,
  REPORT_REASON,
  REPORT_STATUS_TEXT,
  DRAFT_TYPE,
  FILE_BIZ,
  labelOf
};
