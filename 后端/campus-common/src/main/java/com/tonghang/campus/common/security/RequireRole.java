package com.tonghang.campus.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色判定注解。
 *
 * <p>来源：《11-系统概要设计文档》§3.1「角色判定（PermissionChecker，AOP 注解 @RequireRole）：
 * {@code @RequireRole(USER)} 登录即可；{@code @RequireRole(VERIFIED)} 需学号认证」。
 *
 * <p>用法：
 * <pre>
 * &#64;RequireRole(Role.USER)       // 发布动态/商品/搭子、报名、申请组队
 * &#64;RequireRole(Role.VERIFIED)   // 发布活动/竞赛、管理报名
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 游客（仅用于显式标注读接口） */
    int GUEST = 0;
    /** 普通用户：登录即可 */
    int USER = 1;
    /** 认证用户：user_type = 2 */
    int VERIFIED = 2;

    /** 所需最低角色 */
    int value() default USER;

    /** 拒绝时的提示语，留空使用错误码默认文案 */
    String message() default "";
}
