package com.tonghang.campus.common.security;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link RequireRole} 切面：注解式权限判定。
 *
 * <p>来源：《11-系统概要设计文档》§3.1「角色判定（PermissionChecker，AOP 注解 @RequireRole）」。
 *
 * <p>认证口径（阶段二四处决策之①）：
 * <ul>
 *   <li>用户类型以库中实时 {@code user_type} 为准（{@link CurrentUserProvider}），
 *       避免认证通过后旧 Token 快照仍为普通用户；</li>
 *   <li>{@code Verified} 判定 {@code user_type == 2}，对应《10》FR-M1-06 四态中的 2 已认证。</li>
 * </ul>
 */
@Aspect
@Component
public class RequireRoleAspect {

    private final ObjectProvider<CurrentUserProvider> userProvider;

    public RequireRoleAspect(ObjectProvider<CurrentUserProvider> userProvider) {
        this.userProvider = userProvider;
    }

    @Around("@annotation(com.tonghang.campus.common.security.RequireRole) "
            + "|| @within(com.tonghang.campus.common.security.RequireRole)")
    public Object check(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();

        RequireRole anno = AnnotationUtils.findAnnotation(method, RequireRole.class);
        if (anno == null) {
            anno = AnnotationUtils.findAnnotation(pjp.getTarget().getClass(), RequireRole.class);
        }
        if (anno == null) {
            return pjp.proceed();
        }

        LoginUser user = resolveRealtime(UserContext.get());
        int need = anno.value();

        if (need >= RequireRole.USER && user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED,
                    anno.message().isEmpty() ? ErrorCode.UNAUTHORIZED.getMsg() : anno.message());
        }
        if (need >= RequireRole.VERIFIED && !user.isVerified()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    anno.message().isEmpty() ? ErrorCode.FORBIDDEN.getMsg() : anno.message());
        }

        // 回写实时快照，供业务层 UserContext.get() 使用
        if (user != UserContext.get()) {
            UserContext.set(user);
        }
        return pjp.proceed();
    }

    /**
     * 以库中实时值刷新 Token 快照；取不到实现时退化为 Token 快照。
     */
    private LoginUser resolveRealtime(LoginUser tokenUser) {
        if (tokenUser == null) {
            return null;
        }
        CurrentUserProvider provider = userProvider.getIfAvailable();
        if (provider == null) {
            return tokenUser;
        }
        LoginUser realtime = provider.loadRealtime(tokenUser.getUserId());
        return realtime == null ? tokenUser : realtime;
    }
}
