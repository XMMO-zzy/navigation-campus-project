package com.tonghang.campus.common.constant;

/**
 * 全局枚举字典（与前端 framework/constants.js 一一对应）。
 *
 * <p>来源：《12-数据库设计文档》§2 表字段定义；值域不得随意变更，
 * 变更需同步更新《12》《13》与前端 constants.js。
 */
public final class Enums {

    private Enums() {
    }

    /* ---------------- 用户（《12》§2.1 user） ---------------- */

    /** 用户类型 */
    public static final class UserType {
        public static final int NORMAL = 1;    // 普通用户
        public static final int VERIFIED = 2;  // 认证用户

        private UserType() {
        }
    }

    /** 认证四态（《10》FR-M1-06） */
    public static final class VerifyStatus {
        public static final int NONE = 0;      // 未认证
        public static final int PENDING = 1;   // 认证中
        public static final int PASSED = 2;    // 已认证
        public static final int REJECTED = 3;  // 未通过

        private VerifyStatus() {
        }
    }

    /* ---------------- 订单状态机（《12》§2.4 goods_order） ---------------- */

    public static final class OrderStatus {
        public static final int WAIT_SELLER = 10;   // 待卖家确认
        public static final int WAIT_PAY = 20;      // 待付款（30 分钟超时）
        public static final int WAIT_MEET = 30;     // 待面交
        public static final int WAIT_CONFIRM = 40;  // 待双方确认（面交双向确认）
        public static final int FINISHED = 50;      // 已完成
        public static final int CANCELED = 60;      // 已取消
        public static final int TIMEOUT = 70;       // 超时关闭

        private OrderStatus() {
        }
    }

    /* ---------------- 报名状态（《12》§2.5） ---------------- */

    public static final class EnrollStatus {
        public static final int PENDING = 0;
        public static final int PASSED = 1;
        public static final int REJECTED = 2;
        public static final int SIGNED = 3;
        public static final int CANCELED = 4;

        private EnrollStatus() {
        }
    }

    /* ---------------- 组队申请（《12》§2.3 team_apply） ---------------- */

    public static final class ApplyStatus {
        public static final int PENDING = 0;
        public static final int PASSED = 1;
        public static final int REJECTED = 2;
        public static final int WITHDRAWN = 3;
        public static final int ASKING = 4;   // 补充询问

        private ApplyStatus() {
        }
    }

    /* ---------------- 内容可见性 / 评论权限（《12》§2.2 post） ---------------- */

    public static final class VisibleScope {
        public static final int PUBLIC = 0;
        public static final int FOLLOWERS = 1;
        public static final int PRIVATE = 2;

        private VisibleScope() {
        }
    }

    public static final class CommentPermission {
        public static final int ALL = 0;
        public static final int FOLLOWERS = 1;
        public static final int SELF_ONLY = 2;
        public static final int CLOSED = 3;

        private CommentPermission() {
        }
    }

    /* ---------------- 搭子（《12》§2.6 buddy_post） ---------------- */

    public static final class BuddyType {
        public static final int STUDY = 1;
        public static final int SPORT = 2;
        public static final int ENTERTAIN = 3;
        public static final int TRAVEL = 4;
        public static final int MEAL = 5;
        public static final int SKILL = 6;

        private BuddyType() {
        }
    }

    public static final class BuddyFeeMode {
        public static final int AA = 0;
        public static final int TREAT = 1;

        private BuddyFeeMode() {
        }
    }

    /** 性别限制：0 不限 / 1 仅女性（女性专属模式） */
    public static final class GenderLimit {
        public static final int NONE = 0;
        public static final int FEMALE_ONLY = 1;

        private GenderLimit() {
        }
    }

    /* ---------------- 通知业务类型（《12》§2.3 notify_message） ---------------- */

    public static final class NotifyBiz {
        public static final int TRADE = 10;
        public static final int TEAM = 20;
        public static final int INTERACT = 30;
        public static final int SYSTEM = 40;
        public static final int EVENT = 50;
        public static final int PLATFORM = 60;

        private NotifyBiz() {
        }
    }

    /* ---------------- 文件业务类型（POST /files/upload bizType） ---------------- */

    public static final class FileBiz {
        public static final int AVATAR = 1;
        public static final int POST = 2;
        public static final int GOODS = 3;
        public static final int ACTIVITY = 4;
        public static final int COMPETITION = 5;
        public static final int BUDDY = 6;
        public static final int VERIFY_CARD = 7;
        public static final int REPORT = 8;
        public static final int FEEDBACK = 9;

        private FileBiz() {
        }
    }

    /* ---------------- 草稿类型（《12》§2.7 draft） ---------------- */

    public static final class DraftType {
        public static final int POST = 1;
        public static final int GOODS = 2;
        public static final int ACTIVITY = 3;
        public static final int COMPETITION = 4;
        public static final int BUDDY = 5;

        private DraftType() {
        }
    }

    /* ---------------- 多态目标类型（《12》§1.3 通用多态字段） ---------------- */

    public static final class TargetType {
        public static final int POST = 1;
        public static final int GOODS = 2;
        public static final int ACTIVITY = 3;
        public static final int COMPETITION = 4;
        public static final int BUDDY = 5;
        public static final int COMMENT = 6;
        /** 订单（通知 link_type 专用，非《12》follow 值域） */
        public static final int ORDER = 7;
        /** 队伍 */
        public static final int TEAM = 8;

        private TargetType() {
        }
    }

    /* ---------------- 审核状态（《12》通用 status） ---------------- */

    /** 内容审核状态：0 待审 1 通过 2 拦截/下架 3 退回 */
    public static final class AuditStatus {
        public static final int PENDING = 0;
        public static final int PASSED = 1;
        public static final int REJECTED = 2;
        public static final int RETURNED = 3;

        private AuditStatus() {
        }
    }

    /* ---------------- 商品状态（《12》§2.4 goods.status） ---------------- */

    public static final class GoodsStatus {
        public static final int PENDING = 0;   // 待审
        public static final int ON_SALE = 1;   // 在售
        public static final int OFF_SALE = 2;  // 已下架
        public static final int SOLD = 3;      // 已售出
        public static final int DELETED = 4;   // 已删除
        public static final int RETURNED = 5;  // 审核退回

        private GoodsStatus() {
        }
    }

    /* ---------------- 活动状态（《12》§2.5 activity.status） ---------------- */

    public static final class ActivityStatus {
        public static final int PENDING = 0;    // 待审
        public static final int ENROLLING = 1;  // 报名中
        public static final int ONGOING = 2;    // 进行中
        public static final int ENDED = 3;      // 已结束
        public static final int CANCELED = 4;   // 已取消

        private ActivityStatus() {
        }
    }

    /* ---------------- 竞赛状态（《12》§2.5 competition.status） ---------------- */

    public static final class CompetitionStatus {
        public static final int ENROLLING = 1;  // 报名中
        public static final int ONGOING = 2;    // 进行中
        public static final int ENDED = 3;      // 已结束
        public static final int CANCELED = 4;   // 已取消

        private CompetitionStatus() {
        }
    }

    /* ---------------- 信用分变动动作（《12》§2.4 credit_log） ---------------- */

    public static final class CreditAction {
        public static final int ORDER_FINISH = 1;    // 订单完成 +
        public static final int ORDER_CANCEL = 2;    // 违约取消 -
        public static final int REPORT_VALID = 3;    // 举报成立 -
        public static final int REVIEW_SCORE = 4;    // 互评得分
        public static final int ADMIN_ADJUST = 5;    // 后台调整

        private CreditAction() {
        }
    }
}
