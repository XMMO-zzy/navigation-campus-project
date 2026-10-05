package com.tonghang.campus.common.security;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;

/**
 * 登录态上下文（ThreadLocal）。
 *
 * <p>由 {@link AuthInterceptor} 在请求进入时写入、离开时清理；
 * 业务层通过 {@link #requireLogin()} / {@link #requireVerified()} 做权限判定，
 * 对应《11-系统概要设计文档》§3.1「三层判定」。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前用户是否已登录 */
    public static boolean isLogin() {
        return HOLDER.get() != null;
    }

    /** 取当前用户 id，未登录返回 null（游客可读场景） */
    public static Long userIdOrNull() {
        LoginUser u = HOLDER.get();
        return u == null ? null : u.getUserId();
    }

    /**
     * 要求已登录，否则抛 40001。
     * 用于写操作（发布动态/商品/搭子、报名、申请组队）。
     */
    public static Long requireLogin() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return u.getUserId();
    }

    /**
     * 要求认证用户（user_type=2），否则抛 40003。
     * 用于发布活动/竞赛、管理报名（见《11》§3.1 @RequireRole(VERIFIED)）。
     */
    public static Long requireVerified() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!u.isVerified()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return u.getUserId();
    }
}
