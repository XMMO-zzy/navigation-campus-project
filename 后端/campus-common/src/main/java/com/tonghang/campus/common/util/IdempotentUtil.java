package com.tonghang.campus.common.util;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;

/**
 * 幂等与通用校验工具。
 *
 * <p>来源：《13》§1「幂等：写操作带 clientId（前端 UUID）防重复提交」。
 */
public final class IdempotentUtil {

    private IdempotentUtil() {
    }

    /**
     * 校验 clientId 已提供。
     *
     * <p>说明：真正的去重由 Redis SETNX（key = clientId，TTL 10 分钟）在 Service 内完成；
     * 此方法仅做入参前置校验，避免空值落库。
     */
    public static String requireClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少 clientId（幂等键）");
        }
        if (clientId.length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "clientId 长度不能超过 64");
        }
        return clientId.trim();
    }
}
