-- ==========================================================================
-- V1__init_schema.sql
-- 通航校园 · 初始库表（30 张）
-- 来源：《12-数据库设计文档》§1.1 表域划分 / §2 表结构定义
-- 约定：InnoDB / utf8mb4_general_ci；金额 DECIMAL(10,2)；时间 DATETIME
-- ==========================================================================

SET NAMES utf8mb4;

-- ==========================================================================
-- 一、用户域（M1 / BE-D）：6 张
-- ==========================================================================

CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `nickname`      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称',
  `avatar_url`    VARCHAR(512)          DEFAULT NULL COMMENT '头像 COS 地址',
  `gender`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未知 1 男 2 女',
  `school_id`     BIGINT       NOT NULL DEFAULT 1 COMMENT '学校 id（试点固定 1）',
  `major`         VARCHAR(64)           DEFAULT NULL COMMENT '专业',
  `grade`         VARCHAR(16)           DEFAULT NULL COMMENT '年级，如 2024 级',
  `bio`           VARCHAR(200)          DEFAULT NULL COMMENT '简介',
  `skill_tags`    JSON                  DEFAULT NULL COMMENT '技能标签',
  `hobby_tags`    JSON                  DEFAULT NULL COMMENT '爱好标签',
  `personality`   VARCHAR(64)           DEFAULT NULL COMMENT '性格标签',
  `user_type`     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 普通 2 认证',
  `verify_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未认证 1 认证中 2 已认证 3 未通过',
  `credit_score`  INT          NOT NULL DEFAULT 100 COMMENT '信用分',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 正常 1 禁言 2 封禁',
  `deleted_at`    DATETIME              DEFAULT NULL COMMENT '软删（注销）',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_school_major` (`school_id`, `major`),
  KEY `idx_verify` (`verify_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主表';

CREATE TABLE `user_credential` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL COMMENT '关联 user.id',
  `cred_type`  TINYINT      NOT NULL COMMENT '1 微信 openid 2 手机号 3 邮箱',
  `identifier` VARCHAR(128) NOT NULL COMMENT '凭证（手机号/邮箱 AES 加密）',
  `verified`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已验证',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_ident` (`cred_type`, `identifier`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录凭证';

CREATE TABLE `user_verify_apply` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`             BIGINT       NOT NULL COMMENT '申请人',
  `student_no`          VARCHAR(32)  NOT NULL COMMENT '学号',
  `real_name`           VARCHAR(64)  NOT NULL COMMENT '姓名（AES 加密）',
  `campus_card_file_id` BIGINT                DEFAULT NULL COMMENT '校园卡照片 file_object.id',
  `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审核 1 通过 2 驳回',
  `reject_reason`       VARCHAR(200)          DEFAULT NULL COMMENT '驳回原因',
  `reviewed_by`         BIGINT                DEFAULT NULL COMMENT '审核管理员',
  `reviewed_at`         DATETIME              DEFAULT NULL,
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学号认证申请';

CREATE TABLE `follow` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `follower_id` BIGINT   NOT NULL COMMENT '关注者',
  `target_type` TINYINT  NOT NULL COMMENT '1 用户 9 话题',
  `target_id`   BIGINT   NOT NULL COMMENT '被关注对象',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow` (`follower_id`, `target_type`, `target_id`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注';

CREATE TABLE `blacklist` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT   NOT NULL COMMENT '操作人',
  `blocked_user_id` BIGINT   NOT NULL COMMENT '被屏蔽人',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blk` (`user_id`, `blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='屏蔽黑名单';

CREATE TABLE `user_privacy` (
  `id`                BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`           BIGINT   NOT NULL,
  `profile_scope`     TINYINT  NOT NULL DEFAULT 0 COMMENT '资料可见范围 0 公开 1 仅关注 2 私密',
  `post_scope`        TINYINT  NOT NULL DEFAULT 0 COMMENT '动态可见范围',
  `contact_scope`     TINYINT  NOT NULL DEFAULT 0 COMMENT '联系方式可见范围',
  `recommend_enabled` TINYINT  NOT NULL DEFAULT 1 COMMENT '个性化推荐开关',
  `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='隐私设置';

-- ==========================================================================
-- 二、内容域（M2 / BE-E）：7 张
-- ==========================================================================

CREATE TABLE `post` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`            BIGINT       NOT NULL COMMENT '发布者',
  `post_type`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 图文 2 经验分享 3 学习打卡 4 组队需求',
  `content`            TEXT                  DEFAULT NULL COMMENT '正文',
  `images`             JSON                  DEFAULT NULL COMMENT '图片 file_object.id 列表',
  `link_type`          TINYINT               DEFAULT NULL COMMENT '关联内容类型',
  `link_id`            BIGINT                DEFAULT NULL COMMENT '关联内容 id',
  `visible_scope`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 公开 1 仅关注 2 私密',
  `comment_permission` TINYINT      NOT NULL DEFAULT 0 COMMENT '0 所有人 1 关注的人 2 仅自己 3 关闭',
  `like_cnt`           INT          NOT NULL DEFAULT 0,
  `comment_cnt`        INT          NOT NULL DEFAULT 0,
  `favorite_cnt`       INT          NOT NULL DEFAULT 0,
  `view_cnt`           INT          NOT NULL DEFAULT 0,
  `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审 1 已通过 2 已下架 3 审核退回',
  `is_top`             TINYINT      NOT NULL DEFAULT 0 COMMENT '后台置顶',
  `deleted_at`         DATETIME              DEFAULT NULL,
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_user` (`user_id`),
  FULLTEXT KEY `ft_content` (`content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态';

CREATE TABLE `topic` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(32) NOT NULL COMMENT '话题名',
  `type`       TINYINT     NOT NULL DEFAULT 7 COMMENT '1 商品 2 搭子 3 竞赛 4 活动 5 学校 6 技能 7 校园',
  `use_count`  INT         NOT NULL DEFAULT 0 COMMENT '使用次数',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话题';

CREATE TABLE `post_topic_rel` (
  `id`       BIGINT NOT NULL AUTO_INCREMENT,
  `post_id`  BIGINT NOT NULL,
  `topic_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pt` (`post_id`, `topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态-话题关联';

CREATE TABLE `comment` (
  `id`               BIGINT   NOT NULL AUTO_INCREMENT,
  `post_id`          BIGINT   NOT NULL,
  `user_id`          BIGINT   NOT NULL,
  `parent_id`        BIGINT            DEFAULT NULL COMMENT '父评论（楼中楼）',
  `reply_to_user_id` BIGINT            DEFAULT NULL,
  `content`          VARCHAR(500) NOT NULL,
  `like_cnt`         INT      NOT NULL DEFAULT 0,
  `status`           TINYINT  NOT NULL DEFAULT 1 COMMENT '0 待审 1 正常 2 已删除',
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post` (`post_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

CREATE TABLE `like_record` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `target_type` TINYINT  NOT NULL COMMENT '1 动态 6 评论',
  `target_id`   BIGINT   NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_like` (`user_id`, `target_type`, `target_id`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录';

CREATE TABLE `favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `target_type` TINYINT  NOT NULL COMMENT '多态目标类型，见《12》§1.3',
  `target_id`   BIGINT   NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fav` (`user_id`, `target_type`, `target_id`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

CREATE TABLE `browse_history` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `target_type` TINYINT  NOT NULL,
  `target_id`   BIGINT   NOT NULL,
  `viewed_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览历史';

CREATE TABLE `sensitive_word` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(64) NOT NULL,
  `level`      TINYINT     NOT NULL DEFAULT 1 COMMENT '1 拦截 2 复审',
  `status`     TINYINT     NOT NULL DEFAULT 1 COMMENT '0 停用 1 启用',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词';

-- ==========================================================================
-- 三、协作域（M3 / BE-F）：5 张
-- ==========================================================================

CREATE TABLE `notify_message` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL COMMENT '接收者',
  `biz_type`   TINYINT      NOT NULL COMMENT '10 交易 20 组队 30 互动 40 系统 50 活动竞赛 60 平台',
  `title`      VARCHAR(64)  NOT NULL,
  `content`    VARCHAR(500)          DEFAULT NULL,
  `link_type`  TINYINT               DEFAULT NULL,
  `link_id`    BIGINT                DEFAULT NULL,
  `is_read`    TINYINT      NOT NULL DEFAULT 0,
  `read_at`    DATETIME              DEFAULT NULL,
  `dedup_key`  VARCHAR(64)           DEFAULT NULL COMMENT '去重键 user+biz+date',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dedup` (`dedup_key`),
  KEY `idx_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

CREATE TABLE `team` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `name`         VARCHAR(64)  NOT NULL,
  `leader_id`    BIGINT       NOT NULL COMMENT '队长',
  `biz_type`     TINYINT      NOT NULL COMMENT '3 活动 4 竞赛',
  `biz_id`       BIGINT                DEFAULT NULL COMMENT '关联活动/竞赛 id',
  `quota`        INT          NOT NULL DEFAULT 5 COMMENT '人数上限',
  `announcement` VARCHAR(500)          DEFAULT NULL COMMENT '队伍公告',
  `slogan`       VARCHAR(100)          DEFAULT NULL COMMENT '队伍宣言',
  `duty_plan`    JSON                  DEFAULT NULL COMMENT '分工计划',
  `progress`     VARCHAR(500)          DEFAULT NULL COMMENT '进度',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 招募中 1 已满 2 已关闭',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍';

CREATE TABLE `team_member` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `team_id`    BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `role`       TINYINT  NOT NULL DEFAULT 1 COMMENT '0 队长 1 成员',
  `duty`       VARCHAR(64)       DEFAULT NULL COMMENT '分工',
  `joined_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tm` (`team_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍成员';

CREATE TABLE `team_apply` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `team_id`    BIGINT       NOT NULL,
  `user_id`    BIGINT       NOT NULL COMMENT '发起方；direction=2 时为被邀请人',
  `direction`  TINYINT      NOT NULL DEFAULT 1 COMMENT '1 我发出的申请 2 我收到的邀请',
  `message`    VARCHAR(200)          DEFAULT NULL COMMENT '申请理由/邀请说明',
  `reply_msg`  VARCHAR(200)          DEFAULT NULL COMMENT '补充询问内容',
  `skills`     JSON                  DEFAULT NULL COMMENT '展示技能',
  `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已通过 2 已拒绝 3 已撤回 4 补充询问',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_team_status` (`team_id`, `status`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组队申请与邀请';

CREATE TABLE `official_notice` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `type`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 官方通知 2 规则更新 3 平台公告',
  `source`     VARCHAR(64)           DEFAULT NULL COMMENT '来源（部门/组织）',
  `title`      VARCHAR(100) NOT NULL,
  `content`    VARCHAR(2000)         DEFAULT NULL,
  `link_url`   VARCHAR(512)          DEFAULT NULL,
  `publish_at` DATETIME              DEFAULT NULL,
  `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '0 草稿 1 已发布',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_time` (`type`, `publish_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='官方通知';

-- ==========================================================================
-- 四、交易域（M4 / BE-G）：4 张
-- ==========================================================================

CREATE TABLE `goods` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT        NOT NULL COMMENT '卖家',
  `title`        VARCHAR(60)   NOT NULL COMMENT '标题',
  `category_id`  BIGINT        NOT NULL COMMENT '品类（sys_config 字典）',
  `price`        DECIMAL(10,2) NOT NULL COMMENT '现价',
  `orig_price`   DECIMAL(10,2)          DEFAULT NULL COMMENT '原价',
  `cond`         TINYINT       NOT NULL DEFAULT 3 COMMENT '成色 1~5',
  `dims`         VARCHAR(60)            DEFAULT NULL COMMENT '尺寸（长宽高）',
  `description`  VARCHAR(1000)          DEFAULT NULL COMMENT '简介',
  `images`       JSON                   DEFAULT NULL COMMENT '六面展示图',
  `trade_place`  VARCHAR(64)            DEFAULT NULL COMMENT '交易地点',
  `contact`      VARCHAR(64)            DEFAULT NULL COMMENT '联系方式（下单后可见）',
  `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待审 1 在售 2 已下架 3 已售出 4 已删除 5 审核退回',
  `view_cnt`     INT           NOT NULL DEFAULT 0,
  `favorite_cnt` INT           NOT NULL DEFAULT 0,
  `deleted_at`   DATETIME               DEFAULT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cat_status_time` (`category_id`, `status`, `created_at`),
  KEY `idx_user` (`user_id`),
  FULLTEXT KEY `ft_title` (`title`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手商品';

CREATE TABLE `goods_order` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `order_no`           VARCHAR(32)   NOT NULL COMMENT '唯一单号',
  `goods_id`           BIGINT        NOT NULL,
  `buyer_id`           BIGINT        NOT NULL,
  `seller_id`          BIGINT        NOT NULL,
  `amount`             DECIMAL(10,2) NOT NULL COMMENT '成交价',
  `status`             TINYINT       NOT NULL DEFAULT 10 COMMENT '10 待卖家确认 20 待付款 30 待面交 40 待确认 50 已完成 60 已取消 70 超时关闭',
  `buyer_confirm_at`   DATETIME               DEFAULT NULL COMMENT '买家面交确认时间',
  `seller_confirm_at`  DATETIME               DEFAULT NULL COMMENT '卖家面交确认时间',
  `pay_timeout_at`     DATETIME               DEFAULT NULL COMMENT '30min 付款超时',
  `confirm_deadline`   DATETIME               DEFAULT NULL COMMENT '72h 确认截止',
  `meet_place`         VARCHAR(64)            DEFAULT NULL COMMENT '约定面交地点',
  `cancel_reason`      VARCHAR(100)           DEFAULT NULL,
  `buyer_score`        TINYINT                DEFAULT NULL COMMENT '买家评分（P2 互评）',
  `seller_score`       TINYINT                DEFAULT NULL COMMENT '卖家评分（P2 互评）',
  `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_no` (`order_no`),
  KEY `idx_buyer` (`buyer_id`, `status`),
  KEY `idx_seller` (`seller_id`, `status`),
  KEY `idx_deadline` (`confirm_deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手订单';

CREATE TABLE `goods_wanted` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `title`      VARCHAR(60)  NOT NULL COMMENT '求购标题',
  `keywords`   JSON                  DEFAULT NULL COMMENT '关键词（反向推送匹配）',
  `desc`       VARCHAR(500)          DEFAULT NULL,
  `contact`    VARCHAR(64)           DEFAULT NULL,
  `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审 1 有效 2 已关闭',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求购帖';

CREATE TABLE `credit_log` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT      NOT NULL,
  `delta`      INT         NOT NULL COMMENT '分差（正加负减）',
  `biz_type`   TINYINT     NOT NULL COMMENT '10 交易 20 组队 30 举报',
  `biz_id`     BIGINT               DEFAULT NULL,
  `remark`     VARCHAR(200)         DEFAULT NULL,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用流水';

-- ==========================================================================
-- 五、活动竞赛域（M5 / BE-G）：4 张
-- ==========================================================================

CREATE TABLE `activity` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `publisher_id`    BIGINT        NOT NULL COMMENT '发布者（须认证用户）',
  `title`           VARCHAR(60)   NOT NULL,
  `cover`           VARCHAR(512)           DEFAULT NULL,
  `content`         VARCHAR(2000)          DEFAULT NULL,
  `category`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 校园活动 2 学术讲座 3 学术调研',
  `organizer`       VARCHAR(64)            DEFAULT NULL COMMENT '主办方',
  `contact`         VARCHAR(64)            DEFAULT NULL,
  `place`           VARCHAR(128)           DEFAULT NULL,
  `capacity`        INT                    DEFAULT NULL COMMENT '人数上限',
  `enrolled_cnt`    INT           NOT NULL DEFAULT 0,
  `start_at`        DATETIME               DEFAULT NULL,
  `end_at`          DATETIME               DEFAULT NULL,
  `enroll_start`    DATETIME               DEFAULT NULL,
  `enroll_deadline` DATETIME               DEFAULT NULL,
  `need_review`     TINYINT       NOT NULL DEFAULT 0 COMMENT '报名是否需审核',
  `requirement`     VARCHAR(500)           DEFAULT NULL COMMENT '参与要求',
  `rule`            VARCHAR(500)           DEFAULT NULL COMMENT '活动规则',
  `status`          TINYINT       NOT NULL DEFAULT 0 COMMENT '0 待审 1 报名中 2 进行中 3 已结束 4 已取消',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_deadline` (`enroll_deadline`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_publisher` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动';

CREATE TABLE `activity_enroll` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `activity_id` BIGINT       NOT NULL,
  `user_id`     BIGINT       NOT NULL,
  `name`        VARCHAR(32)           DEFAULT NULL COMMENT '报名姓名',
  `phone`       VARCHAR(20)           DEFAULT NULL COMMENT '联系手机号',
  `apply_msg`   VARCHAR(200)          DEFAULT NULL COMMENT '备注',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审核 1 通过 2 拒绝 3 已签到 4 已取消',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ae` (`activity_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名';

CREATE TABLE `competition` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `publisher_id`    BIGINT       NOT NULL COMMENT '发布者（须认证用户）',
  `title`           VARCHAR(60)  NOT NULL,
  `cover`           VARCHAR(512)          DEFAULT NULL,
  `content`         VARCHAR(2000)         DEFAULT NULL,
  `level`           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 校级 2 省级 3 国家级',
  `organizer`       VARCHAR(64)           DEFAULT NULL,
  `contact`         VARCHAR(64)           DEFAULT NULL,
  `place`           VARCHAR(128)          DEFAULT NULL,
  `capacity`        INT                   DEFAULT NULL,
  `enrolled_cnt`    INT          NOT NULL DEFAULT 0,
  `requirement`     VARCHAR(500)          DEFAULT NULL,
  `awards`          VARCHAR(500)          DEFAULT NULL COMMENT '奖项设置',
  `rule`            VARCHAR(500)          DEFAULT NULL COMMENT '赛程与规则',
  `enroll_start`    DATETIME              DEFAULT NULL,
  `enroll_deadline` DATETIME              DEFAULT NULL,
  `preliminary_at`  DATETIME              DEFAULT NULL COMMENT '初赛时间',
  `final_at`        DATETIME              DEFAULT NULL COMMENT '决赛时间',
  `start_at`        DATETIME              DEFAULT NULL,
  `end_at`          DATETIME              DEFAULT NULL,
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 报名中 2 进行中 3 已结束 4 已取消',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_deadline` (`enroll_deadline`),
  KEY `idx_level` (`level`),
  KEY `idx_publisher` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='竞赛';

CREATE TABLE `competition_enroll` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `competition_id` BIGINT       NOT NULL,
  `user_id`        BIGINT       NOT NULL,
  `team_id`        BIGINT                DEFAULT NULL COMMENT '队伍参赛',
  `name`           VARCHAR(32)           DEFAULT NULL,
  `phone`          VARCHAR(20)           DEFAULT NULL,
  `apply_msg`      VARCHAR(500)          DEFAULT NULL COMMENT '报名信息',
  `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审核 1 通过 2 拒绝 3 已取消',
  `result`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 无 1 一等奖 2 二等奖 3 三等奖 4 优秀奖 5 入围',
  `award_desc`     VARCHAR(200)          DEFAULT NULL COMMENT '获奖说明',
  `work_intro`     VARCHAR(1000)         DEFAULT NULL COMMENT '作品介绍',
  `work_link`      VARCHAR(512)          DEFAULT NULL COMMENT '演示链接',
  `work_files`     JSON                  DEFAULT NULL COMMENT '附件 file_object.id',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ce` (`competition_id`, `user_id`),
  KEY `idx_team` (`competition_id`, `team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='竞赛报名与成果';

-- ==========================================================================
-- 六、搭子域（M6 / BE-G）：2 张
-- ==========================================================================

CREATE TABLE `buddy_post` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL COMMENT '发布者',
  `title`        VARCHAR(60)  NOT NULL COMMENT '标题',
  `buddy_type`   TINYINT      NOT NULL DEFAULT 1 COMMENT '1 学习 2 运动 3 娱乐 4 旅行 5 饭搭子 6 技能',
  `tags`         JSON                  DEFAULT NULL COMMENT '兴趣标签',
  `desc`         VARCHAR(500)          DEFAULT NULL COMMENT '展示信息（性别/爱好/年龄/性格）',
  `place`        VARCHAR(64)           DEFAULT NULL COMMENT '活动地点',
  `time_desc`    VARCHAR(64)           DEFAULT NULL COMMENT '时间安排',
  `requirement`  VARCHAR(200)          DEFAULT NULL COMMENT '对搭子要求',
  `images`       JSON                  DEFAULT NULL COMMENT '图片 file_object.id',
  `contact`      VARCHAR(64)           DEFAULT NULL COMMENT '联系方式（通过后可见）',
  `gender_limit` TINYINT      NOT NULL DEFAULT 0 COMMENT '0 不限 1 仅女性（女性专属模式）',
  `quota`        INT          NOT NULL DEFAULT 2 COMMENT '人数上限',
  `fee_mode`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 AA 1 请客',
  `matched`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 寻找中 1 已找到',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审 1 正常 2 已关闭',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_status` (`buddy_type`, `status`, `created_at`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搭子帖';

CREATE TABLE `buddy_apply` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `buddy_id`   BIGINT       NOT NULL,
  `user_id`    BIGINT       NOT NULL COMMENT '申请人',
  `message`    VARCHAR(200)          DEFAULT NULL COMMENT '申请说明',
  `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待处理 1 已通过 2 已拒绝 3 已撤回',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ba` (`buddy_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搭子申请';

-- ==========================================================================
-- 七、支撑域（M7 / BE-D）：6 张
-- ==========================================================================

CREATE TABLE `report` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `reporter_id` BIGINT       NOT NULL,
  `target_type` TINYINT      NOT NULL COMMENT '多态目标类型',
  `target_id`   BIGINT       NOT NULL,
  `reason_type` TINYINT      NOT NULL COMMENT '1 虚假 2 诈骗 3 侵权 4 违规 5 其他',
  `desc`        VARCHAR(500)          DEFAULT NULL,
  `evidence`    JSON                  DEFAULT NULL COMMENT '证据 file_object.id',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待处理 1 处理中 2 已处理 3 驳回',
  `handler_id`  BIGINT                DEFAULT NULL,
  `result`      VARCHAR(500)          DEFAULT NULL,
  `handled_at`  DATETIME              DEFAULT NULL COMMENT '24h SLA 统计口径',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `created_at`),
  KEY `idx_reporter` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报工单';

CREATE TABLE `draft` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT   NOT NULL,
  `draft_type` TINYINT  NOT NULL COMMENT '1 动态 2 商品 3 活动 4 竞赛 5 搭子',
  `payload`    JSON              DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft` (`user_id`, `draft_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='草稿';

CREATE TABLE `file_object` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL,
  `biz_type`     TINYINT      NOT NULL COMMENT '1 头像 2 动态 3 商品 4 活动 5 竞赛 6 搭子 7 认证 8 举报 9 反馈',
  `url`          VARCHAR(512) NOT NULL,
  `object_key`   VARCHAR(512)          DEFAULT NULL COMMENT 'COS 对象键',
  `size`         BIGINT       NOT NULL DEFAULT 0,
  `mime`         VARCHAR(64)           DEFAULT NULL,
  `audit_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审 1 通过 2 拦截',
  `audit_remark` VARCHAR(200)          DEFAULT NULL COMMENT '审核失败原因',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件对象';

CREATE TABLE `feedback` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `type`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 功能建议 2 缺陷反馈 3 内容投诉 4 其他',
  `content`    VARCHAR(1000) NOT NULL,
  `images`     JSON                  DEFAULT NULL,
  `contact`    VARCHAR(64)           DEFAULT NULL,
  `status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待处理 1 处理中 2 已处理',
  `reply`      VARCHAR(500)          DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈';

CREATE TABLE `sys_config` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `cfg_key`    VARCHAR(64)  NOT NULL,
  `cfg_value`  TEXT                  DEFAULT NULL COMMENT 'JSON 值',
  `remark`     VARCHAR(200)          DEFAULT NULL,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`cfg_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置字典';

CREATE TABLE `audit_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `admin_id`    BIGINT       NOT NULL,
  `action`      VARCHAR(64)  NOT NULL COMMENT '操作动作',
  `target_type` TINYINT               DEFAULT NULL,
  `target_id`   BIGINT                DEFAULT NULL,
  `detail`      VARCHAR(1000)         DEFAULT NULL,
  `ip`          VARCHAR(64)           DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_time` (`admin_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';
