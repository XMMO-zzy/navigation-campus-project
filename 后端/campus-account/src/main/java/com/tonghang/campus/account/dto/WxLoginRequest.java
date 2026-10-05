package com.tonghang.campus.account.dto;

/**
 * 微信一键登录请求体（《13》§4 POST /auth/wx-login）。
 *
 * <pre>{ "code": "0a3xWm000vJ9Q13...", "clientId": "c-8f2a" }</pre>
 */
public class WxLoginRequest {

    /** wx.login 返回的临时 code */
    private String code;
    /** 幂等键（《13》§1） */
    private String clientId;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
