package com.tonghang.campus.common.security;

/**
 * 当前登录用户（由 JWT 解析得到，不落库）。
 *
 * <p>认证口径：《10》FR-M1-06 四态 verify_status(0/1/2/3)；
 * 《11》§3.1 角色判定 user_type=2 为认证用户（VERIFIED）。
 */
public class LoginUser {

    /** user.id */
    private final Long userId;
    /** 1 普通用户 / 2 认证用户 */
    private final Integer userType;
    /** 0 未认证 1 认证中 2 已认证 3 未通过 */
    private final Integer verifyStatus;
    /** access / refresh */
    private final String tokenType;

    public LoginUser(Long userId, Integer userType, Integer verifyStatus, String tokenType) {
        this.userId = userId;
        this.userType = userType;
        this.verifyStatus = verifyStatus;
        this.tokenType = tokenType;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getUserType() {
        return userType;
    }

    public Integer getVerifyStatus() {
        return verifyStatus;
    }

    public String getTokenType() {
        return tokenType;
    }

    /** 是否认证用户（user_type=2，见《10》FR-M1-06 / 《11》§3.1） */
    public boolean isVerified() {
        return userType != null && userType == 2;
    }

    public boolean isAccessToken() {
        return JwtTokenProvider.TYPE_ACCESS.equals(tokenType);
    }
}
