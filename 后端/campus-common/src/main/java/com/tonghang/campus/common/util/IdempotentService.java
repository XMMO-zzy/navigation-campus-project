package com.tonghang.campus.common.util;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 幂等键服务。
 *
 * <p>来源：《13-API接口文档》§1「幂等：写操作带 clientId（前端 UUID）防重复提交」。
 * <p>实现：Redis {@code SETNX campus:idem:{clientId}}，TTL 10 分钟；重复提交 → 40006。
 *
 * <p><b>降级策略（两级，保证本地无 Redis 也能跑通 P0 链路）</b>：
 * <ol>
 *   <li><b>Bean 缺席</b>：容器里没有 {@link StringRedisTemplate} → 直接跳过校验；</li>
 *   <li><b>连接失败</b>：Bean 存在但 Redis 连不上（本地最常见）→ 熔断降级为「放行」，
 *       并记录一次 WARN；{@value #RETRY_INTERVAL_MS} ms 后自动重试一次，
 *       恢复后自动切回正常校验。</li>
 * </ol>
 *
 * <p>⚠️ 注意：降级只影响「防重复提交」，不影响任何业务正确性；
 * 正式环境必须保证 Redis 可用（多实例部署时幂等键是必需的）。
 */
@Component
public class IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);

    private static final String KEY_PREFIX = "campus:idem:";
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 熔断后的重试间隔（毫秒） */
    private static final long RETRY_INTERVAL_MS = 60_000L;

    private final StringRedisTemplate redis;

    /** 是否可用（Redis 连接失败后置 false） */
    private volatile boolean available = true;
    /** 熔断截止时间戳（此时间之前不再尝试连接） */
    private volatile long degradedUntil = 0L;
    /** 上次打 WARN 的时间，避免日志刷屏 */
    private volatile long lastWarnAt = 0L;

    public IdempotentService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
        if (this.redis == null) {
            log.warn("[idempotent] 未装配 Redis，幂等校验已降级为放行（P0 本地联调可接受）");
        }
    }

    /**
     * 校验并占用幂等键。
     *
     * @param clientId 前端生成的 UUID
     * @throws BizException 40006 重复提交
     */
    public void checkAndLock(String clientId) {
        String key = IdempotentUtil.requireClientId(clientId);
        if (shouldSkip()) {
            return;
        }

        // 只有 Redis 调用本身放在 try 内；判定结果在 try 外，避免误吞 DUPLICATE 业务异常
        Boolean ok;
        try {
            ok = redis.opsForValue().setIfAbsent(KEY_PREFIX + key, "1", TTL);
            recoverIfNeeded();
        } catch (RuntimeException e) {
            degrade(e);
            return;
        }

        if (ok == null || !ok) {
            throw new BizException(ErrorCode.DUPLICATE);
        }
    }

    /** 事务失败时释放幂等键，允许用户重试 */
    public void release(String clientId) {
        if (redis == null || clientId == null || clientId.trim().isEmpty() || shouldSkip()) {
            return;
        }
        try {
            redis.delete(KEY_PREFIX + clientId.trim());
            recoverIfNeeded();
        } catch (RuntimeException e) {
            degrade(e);
        }
    }

    /** Redis 是否处于可用状态（供健康检查/排查使用） */
    public boolean isAvailable() {
        return redis != null && available;
    }

    /* ---------------- 内部：熔断与恢复 ---------------- */

    /** 是否需要跳过（Bean 缺席 或 处于熔断窗口内） */
    private boolean shouldSkip() {
        if (redis == null) {
            return true;
        }
        return !available && System.currentTimeMillis() < degradedUntil;
    }

    /** 连接失败 → 熔断降级（节流打日志，不阻断业务） */
    private void degrade(RuntimeException e) {
        available = false;
        long now = System.currentTimeMillis();
        degradedUntil = now + RETRY_INTERVAL_MS;
        if (now - lastWarnAt > RETRY_INTERVAL_MS) {
            lastWarnAt = now;
            log.warn("[idempotent] Redis 不可用，幂等校验临时降级为放行（{}s 后自动重试）：{}",
                    RETRY_INTERVAL_MS / 1000, e.getMessage());
        }
    }

    /** 调用成功 → 若之前处于熔断态则宣告恢复 */
    private void recoverIfNeeded() {
        if (!available) {
            available = true;
            degradedUntil = 0L;
            log.info("[idempotent] Redis 已恢复，幂等校验切回正常模式");
        }
    }
}
