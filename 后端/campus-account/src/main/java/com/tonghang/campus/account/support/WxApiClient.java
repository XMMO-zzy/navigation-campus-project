package com.tonghang.campus.account.support;

/**
 * 微信开放平台接口客户端（M1）。
 *
 * <p>来源：《11-系统概要设计文档》§1.2「M1 --> WX 微信开放平台登录/模板消息/内容安全」。
 *
 * <p>当前为骨架实现：当 application.yml 未配置 {@code campus.wx.app-id} / {@code app-secret} 时，
 * 走本地开发占位（openId = "dev_" + code），便于在无微信联调环境时跑通全链路；
 * 配置齐全后由 {@link #code2OpenIdRemote} 调用真实接口。
 */
public interface WxApiClient {

    /**
     * code 换 openId（《13》§3.1 POST /auth/wx-login）。
     *
     * @param code wx.login 返回的临时 code
     * @return openId
     */
    String code2OpenId(String code);
}
