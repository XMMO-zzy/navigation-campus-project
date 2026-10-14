package com.tonghang.campus.common.redis;

import java.time.Duration;

/**
 * Redis 键规范（统一前缀，避免与宿主/其它服务冲突）。
 *
 * <p>约定：{@code campus:<域>:<子域>:<标识>}，全部小写。
 * 新增键必须在此登记，便于运维排查与批量清理。
 */
public final class RedisKeys {

    /** 全局前缀 */
    public static final String PREFIX = "campus:";

    private RedisKeys() {
    }

    /* ---------------- 幂等（《13》§1） ---------------- */

    public static String idempotent(String clientId) {
        return PREFIX + "idem:" + clientId;
    }

    /* ---------------- 搜索（FR-M2-07） ---------------- */

    /** 用户搜索历史（Redis List，LPUSH 头部为最新） */
    public static String searchHistory(Long userId) {
        return PREFIX + "search:his:" + userId;
    }

    /** 热门搜索词（全局 ZSet，score = 搜索次数） */
    public static final String SEARCH_HOT = PREFIX + "search:hot";

    /** 搜索历史保留条数上限（FR-M2-07 与前端 storage 保持一致） */
    public static final int SEARCH_HISTORY_MAX = 20;

    /** 搜索历史 TTL */
    public static final Duration SEARCH_HISTORY_TTL = Duration.ofDays(30);

    /** 热词榜长度（前端展示 Top10） */
    public static final int SEARCH_HOT_TOP = 10;

    /* ---------------- 消息设置（FR-M3-02） ---------------- */

    /** 用户消息设置（JSON String） */
    public static String notifySetting(Long userId) {
        return PREFIX + "msg:setting:" + userId;
    }

    /* ---------------- 推荐画像（FR-M2-08） ---------------- */

    /** 用户浏览计数（用于判定「≥10 次浏览后切换行为驱动」） */
    public static String browseCounter(Long userId) {
        return PREFIX + "rec:browse:" + userId + ":cnt";
    }

    /** 用户负反馈（不感兴趣）的 post id 集合 */
    public static String dislikedPosts(Long userId) {
        return PREFIX + "rec:dislike:" + userId;
    }
}
