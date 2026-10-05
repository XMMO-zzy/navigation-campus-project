package com.tonghang.campus.common.security;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态拦截器。
 *
 * <p>来源：《11-系统概要设计文档》§3.1：
 * 「无 Token：读接口放行（游客），写接口 40001」。
 * 采用「宽松解析」策略：带 Token 则解析并写入上下文，不带则视为游客；
 * 具体权限由 {@link RequireRoleAspect} 依注解判定。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public AuthInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            if (!token.isEmpty()) {
                try {
                    LoginUser user = tokenProvider.parse(token);
                    // 仅 accessToken 可用于业务接口；refreshToken 只走 /auth/refresh
                    if (user.isAccessToken()) {
                        UserContext.set(user);
                    }
                } catch (BizException e) {
                    // 读接口允许以游客身份继续；写接口由 @RequireRole 兜底 40001/40002
                    if (e.getErrorCode() == ErrorCode.TOKEN_EXPIRED) {
                        request.setAttribute("tokenExpired", Boolean.TRUE);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
