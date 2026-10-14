package com.tonghang.campus.common.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Redis 访问支撑层（统一熔断降级）。
 *
 * <p>背景：本项目 P1 的多个功能把**轻量数据**放在 Redis（《13》§3.2 明示热门搜索走 Redis ZSet；
 * 用户已确认「不改《12》表结构，用 Redis + 本地存储」）：
 * <ul>
 *   <li>搜索历史（per-user List，上限 20，TTL 30 天）</li>
 *   <li>热门搜索词（全局 ZSet，ZINCRBY 计数）</li>
 *   <li>消息设置 / 免打扰（per-user JSON String）</li>
 * </ul>
 *
 * <p>而这些能力**都不应该因为本地没起 Redis 就整体不可用**，
 * 所以统一在此做两级降级（与 {@code IdempotentService} 同一套熔断策略）：
 * <ol>
 *   <li><b>Bean 缺席</b>：容器里没有 {@link StringRedisTemplate} → 一律走 fallback；</li>
 *   <li><b>连接失败</b>：熔断 {@value #RETRY_INTERVAL_MS} ms，期间直接走 fallback，
 *       节流打一次 WARN；恢复后自动切回并打 INFO。</li>
 * </ol>
 *
 * <p>调用约定：所有 Redis 读写都必须经 {@link #call} / {@link #run}，
 * 不要直接拿 Template 干活，否则降级保护会失效。
 */
@Component
public class RedisSupport {

    private static final Logger log = LoggerFactory.getLogger(RedisSupport.class);

    /** 熔断后的重试间隔（毫秒） */
    private static final long RETRY_INTERVAL_MS = 60_000L;

    private final StringRedisTemplate redis;

    private volatile boolean available = true;
    private volatile long degradedUntil = 0L;
    private volatile long lastWarnAt = 0L;

    public RedisSupport(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
        if (this.redis == null) {
            log.warn("[redis] 未装配 StringRedisTemplate，依赖 Redis 的功能（搜索历史/热词/消息设置）已降级");
        }
    }

    /**
     * 执行一次 Redis 操作；不可用或抛异常时返回 {@code fallback}，绝不向上抛。
     *
     * @param action   实际操作
     * @param fallback 降级返回值
     */
    public <T> T call(Function<StringRedisTemplate, T> action, T fallback) {
        if (shouldSkip()) {
            return fallback;
        }
        try {
            T result = action.apply(redis);
            recoverIfNeeded();
            return result;
        } catch (RuntimeException e) {
            degrade(e);
            return fallback;
        }
    }

    /** 无返回值的写操作 */
    public void run(Consumer<StringRedisTemplate> action) {
        call(t -> {
            action.accept(t);
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    /** Redis 当前是否可用（供上层判断要不要走本地兜底） */
    public boolean isAvailable() {
        return redis != null && available;
    }

    /* ---------------- 内部：熔断与恢复 ---------------- */

    private boolean shouldSkip() {
        if (redis == null) {
            return true;
        }
        return !available && System.currentTimeMillis() < degradedUntil;
    }

    private void degrade(RuntimeException e) {
        available = false;
        long now = System.currentTimeMillis();
        degradedUntil = now + RETRY_INTERVAL_MS;
        if (now - lastWarnAt > RETRY_INTERVAL_MS) {
            lastWarnAt = now;
            log.warn("[redis] 连接失败，依赖 Redis 的功能临时降级（{}s 后自动重试）：{}",
                    RETRY_INTERVAL_MS / 1000, e.getMessage());
        }
    }

    private void recoverIfNeeded() {
        if (!available) {
            available = true;
            degradedUntil = 0L;
            log.info("[redis] 已恢复，依赖 Redis 的功能切回正常模式");
        }
    }
}
