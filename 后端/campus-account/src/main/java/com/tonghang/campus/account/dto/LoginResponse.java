package com.tonghang.campus.account.dto;

/**
 * 登录响应体（《13》§4 POST /auth/wx-login Response data）。
 *
 * <pre>
 * {
 *   "accessToken": "eyJhbGciOi...", "refreshToken": "rt-9d8e...",
 *   "expiresIn": 7200,
 *   "user": { "id": 10001, "nickname": "小航", "avatar": "https://cos.../a.jpg",
 *             "userType": 1, "verifyStatus": 0 }
 * }
 * </pre>
 */
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    /** accessToken 有效期（秒） */
    private long expiresIn;
    private UserBrief user;

    public LoginResponse() {
    }

    public LoginResponse(String accessToken, String refreshToken, long expiresIn, UserBrief user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    /** 登录返回的用户摘要 */
    public static class UserBrief {
        private Long id;
        private String nickname;
        private String avatar;
        /** 1 普通用户 2 认证用户 */
        private Integer userType;
        /** 0 未认证 1 认证中 2 已认证 3 未通过 */
        private Integer verifyStatus;

        public UserBrief() {
        }

        public UserBrief(Long id, String nickname, String avatar, Integer userType, Integer verifyStatus) {
            this.id = id;
            this.nickname = nickname;
            this.avatar = avatar;
            this.userType = userType;
            this.verifyStatus = verifyStatus;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public Integer getUserType() {
            return userType;
        }

        public void setUserType(Integer userType) {
            this.userType = userType;
        }

        public Integer getVerifyStatus() {
            return verifyStatus;
        }

        public void setVerifyStatus(Integer verifyStatus) {
            this.verifyStatus = verifyStatus;
        }
    }

    /* ---------------- getter / setter ---------------- */

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserBrief getUser() {
        return user;
    }

    public void setUser(UserBrief user) {
        this.user = user;
    }
}
