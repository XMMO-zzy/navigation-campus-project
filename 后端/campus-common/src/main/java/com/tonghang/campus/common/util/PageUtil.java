package com.tonghang.campus.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页参数规范化。
 *
 * <p>来源：《13-API接口文档》§1「分页：请求 page（从 1）/ size（默认 20，≤50）」。
 */
public final class PageUtil {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    private PageUtil() {
    }

    /** 归一化 page（最小 1） */
    public static int normalizePage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    /** 归一化 size（1~50，默认 20） */
    public static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 构造 MyBatis-Plus 分页对象 */
    public static <T> Page<T> of(Integer page, Integer size) {
        return new Page<>(normalizePage(page), normalizeSize(size));
    }
}
