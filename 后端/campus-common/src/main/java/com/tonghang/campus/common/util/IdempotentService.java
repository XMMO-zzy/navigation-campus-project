package com.tonghang.campus.common.util;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.redis.RedisKeys;
import com.tonghang.campus.common.redis.RedisSupport;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 幂等键服务。
 *
 * <p>来源：《13-API接口文档》§1「幂等：写操作带 clientId（前端 UUID）防重复提交」。
 * <p>实现：Redis {@code SETNX campus:idem:{clientId}}，TTL 10 分钟；重复提交 → 40006。
 *
 * <p><b>降级策略</b>：熔断与恢复逻辑统一收敛在 {@link RedisSupport}
 * （Redis 未启动 / 连不上时自动降级为「放行」，60s 后自动重试）。
 * 此处只保留幂等本身的语义判定，避免同一套熔断逻辑在多处重复实现。
 *
 * <p>⚠️ 注意：降级只影响「防重复提交」，不影响任何业务正确性；
 * 正式环境必须保证 Redis 可用（多实例部署时幂等键是必需的）。
 */
@Component
public class IdempotentService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisSupport redis;

    public IdempotentService(RedisSupport redis) {
        this.redis = redis;
    }

    /**
     * 校验并占用幂等键。
     *
     * @param clientId 前端生成的 UUID
     * @throws BizException 40006 重复提交
     */
    public void checkAndLock(String clientId) {
        String key = IdempotentUtil.requireClientId(clientId);

        // 只有 Redis 调用在 call 内；判定放外面，避免 DUPLICATE 业务异常被降级逻辑吞掉
        Boolean ok = redis.call(
                t -> t.opsForValue().setIfAbsent(RedisKeys.idempotent(key), "1", TTL),
                null);

        if (ok == null) {
            // Redis 不可用 → 降级放行
            return;
        }
        if (!ok) {
            throw new BizException(ErrorCode.DUPLICATE);
        }
    }

    /** 事务失败时释放幂等键，允许用户重试 */
    public void release(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }
        redis.run(t -> t.delete(RedisKeys.idempotent(clientId.trim())));
    }

    /** Redis 是否处于可用状态（供排查使用） */
    public boolean isAvailable() {
        return redis.isAvailable();
    }
}
