package com.tonghang.campus.common.security;

/**
 * 登录用户实时资料提供方（跨模块解耦）。
 *
 * <p>背景：{@link JwtTokenProvider} 签发的 Token 内嵌 {@code userType / verifyStatus} 快照，
 * 认证通过后（异步审核）快照会过期，导致 {@code @RequireRole(VERIFIED)} 误判。
 * 因此权限判定前须以「库中实时值」为准。
 *
 * <p>实现由 campus-account 模块提供（{@code UserProviderImpl}），
 * common 层只依赖接口，避免反向依赖业务模块。
 */
public interface CurrentUserProvider {

    /**
     * 取用户实时登录态。
     *
     * @param userId user.id
     * @return 实时 LoginUser；用户不存在/已注销返回 {@code null}
     */
    LoginUser loadRealtime(Long userId);
}
