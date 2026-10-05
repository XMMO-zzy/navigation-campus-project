package com.tonghang.campus.account.support;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 微信接口客户端默认实现。
 *
 * <p>未配置 appId/appSecret 时启用开发占位模式；已配置时调用
 * {@code https://api.weixin.qq.com/sns/jscode2session}。
 *
 * <p>说明：真实对接需在《16-部署手册》中配置 WX_APP_ID / WX_APP_SECRET 环境变量。
 */
@Component
public class DefaultWxApiClient implements WxApiClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultWxApiClient.class);
    private static final String JSCODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";

    private final String appId;
    private final String appSecret;
    private final RestClient restClient = RestClient.create();

    public DefaultWxApiClient(@Value("${campus.wx.app-id:}") String appId,
                              @Value("${campus.wx.app-secret:}") String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String code2OpenId(String code) {
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            // 开发占位：避免本地无微信环境时阻塞联调
            log.warn("[M1][WX] 未配置 appId/appSecret，使用开发占位 openId。code={}", code);
            return "dev_" + code;
        }
        try {
            Map<String, Object> resp = restClient.get()
                    .uri(JSCODE2SESSION + "?appid={a}&secret={s}&js_code={c}&grant_type=authorization_code",
                            appId, appSecret, code)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                throw new BizException(ErrorCode.WX_API_FAILED, "微信返回为空");
            }
            Object openId = resp.get("openid");
            if (openId == null) {
                Object errmsg = resp.get("errmsg");
                log.error("[M1][WX] jscode2session 失败: {}", resp);
                throw new BizException(ErrorCode.WX_API_FAILED,
                        errmsg == null ? "微信授权失败" : String.valueOf(errmsg));
            }
            return String.valueOf(openId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[M1][WX] 调用 jscode2session 异常", e);
            throw new BizException(ErrorCode.WX_API_FAILED);
        }
    }
}
