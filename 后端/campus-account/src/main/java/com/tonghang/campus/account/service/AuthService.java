package com.tonghang.campus.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.account.dto.LoginResponse;
import com.tonghang.campus.account.dto.WxLoginRequest;
import com.tonghang.campus.account.entity.User;
import com.tonghang.campus.account.entity.UserCredential;
import com.tonghang.campus.account.mapper.UserCredentialMapper;
import com.tonghang.campus.account.mapper.UserMapper;
import com.tonghang.campus.account.support.WxApiClient;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.security.JwtTokenProvider;
import com.tonghang.campus.common.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号与认证服务（M1）。
 *
 * <p>来源：
 * <ul>
 *   <li>《10-需求文档》FR-M1-01 微信一键授权登录 / FR-M1-04 刷新 Token / FR-M1-07 退出 / FR-M1-08 注销</li>
 *   <li>《13-API接口文档》§3.1（POST /auth/wx-login、/auth/refresh、POST /users/logout、/users/deactivate）</li>
 *   <li>《11-系统概要设计文档》§1.3「Service：业务规则、事务边界」</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final UserCredentialMapper credentialMapper;
    private final WxApiClient wxApiClient;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserMapper userMapper,
                       UserCredentialMapper credentialMapper,
                       WxApiClient wxApiClient,
                       JwtTokenProvider tokenProvider) {
        this.userMapper = userMapper;
        this.credentialMapper = credentialMapper;
        this.wxApiClient = wxApiClient;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 微信一键授权登录：code 换 openID，首登建号（FR-M1-01）。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse wxLogin(WxLoginRequest req) {
        if (req == null || req.getCode() == null || req.getCode().trim().isEmpty()) {
            throw BizException.param("缺少微信登录 code");
        }
        String openId = wxApiClient.code2OpenId(req.getCode());

        UserCredential cred = credentialMapper.selectOne(
                new LambdaQueryWrapper<UserCredential>()
                        .eq(UserCredential::getCredType, UserCredential.TYPE_WX_OPENID)
                        .eq(UserCredential::getIdentifier, openId)
                        .last("LIMIT 1"));

        User user;
        if (cred == null) {
            user = createNewUser(openId);
        } else {
            user = userMapper.selectById(cred.getUserId());
            if (user == null || user.getDeletedAt() != null) {
                // 凭证残留但用户已注销 → 重新建号
                user = createNewUser(openId);
            } else if (user.getStatus() != null && user.getStatus() == 2) {
                throw new BizException(ErrorCode.FORBIDDEN, "账号已被封禁，如有疑问请联系管理员");
            }
        }
        return issueTokens(user);
    }

    private User createNewUser(String openId) {
        User user = new User();
        user.setNickname("校园用户" + System.currentTimeMillis() % 100000);
        user.setGender(0);
        user.setSchoolId(1L);
        user.setUserType(1);
        user.setVerifyStatus(0);
        user.setCreditScore(100);
        user.setStatus(0);
        userMapper.insert(user);

        UserCredential cred = new UserCredential();
        cred.setUserId(user.getId());
        cred.setCredType(UserCredential.TYPE_WX_OPENID);
        cred.setIdentifier(openId);
        cred.setVerified(1);
        credentialMapper.insert(cred);

        log.info("[M1] 微信首登建号 userId={}", user.getId());
        return user;
    }

    /**
     * 刷新 accessToken（FR-M1-04）。
     */
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少 refreshToken");
        }
        LoginUser lu = tokenProvider.parse(refreshToken);
        if (!JwtTokenProvider.TYPE_REFRESH.equals(lu.getTokenType())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refreshToken 无效");
        }
        User user = userMapper.selectById(lu.getUserId());
        if (user == null || user.getDeletedAt() != null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return issueTokens(user);
    }

    /** 退出登录（FR-M1-07）：JWT 无状态，服务端仅记录；Redis 黑名单可由网关层补入 */
    public void logout(Long userId) {
        log.info("[M1] 用户退出登录 userId={}", userId);
    }

    /**
     * 手机号验证码登录（FR-M1-02，备用通道）。
     *
     * <p>说明：短信通道（腾讯云 SMS）尚未接入，《13》§3.1 未定义验证码发送接口；
     * 此处保留契约入口，短信校验在通道就绪后补入，暂不支持直接登录。
     */
    public LoginResponse phoneLogin(String phone, String smsCode) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw BizException.param("手机号格式不正确");
        }
        if (smsCode == null || smsCode.trim().isEmpty()) {
            throw BizException.param("请输入短信验证码");
        }
        // 待通道接入：调用 SMS 校验 code → 查/建 user_credential(cred_type=2)
        throw new BizException(ErrorCode.STATUS_CONFLICT, "短信通道尚未接入，请使用微信登录");
    }

    /**
     * 账号注销（FR-M1-08，冷静期 15 天）。
     *
     * <p>说明：注销为异步流程（冷静期内可撤销），完整实现需配合定时任务；
     * 此处按契约返回受理结果与冷静期，不立即软删。
     */
    public java.util.Map<String, Object> deactivate(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw BizException.notFound("账号不存在");
        }
        java.util.Map<String, Object> data = new java.util.HashMap<>(4);
        data.put("accepted", true);
        data.put("coolingOffDays", 15);
        data.put("effectiveAt", java.time.LocalDateTime.now().plusDays(15).toString());
        log.info("[M1] 用户申请注销 userId={}（冷静期 15 天）", userId);
        return data;
    }

    /** 重新签发一对 Token（供 M1 校验/其它模块复用） */
    public LoginResponse issueTokens(User user) {
        String access = tokenProvider.createAccessToken(user.getId(), user.getUserType(), user.getVerifyStatus());
        String refresh = tokenProvider.createRefreshToken(user.getId(), user.getUserType(), user.getVerifyStatus());
        LoginResponse.UserBrief brief = new LoginResponse.UserBrief(
                user.getId(), user.getNickname(), user.getAvatarUrl(),
                user.getUserType(), user.getVerifyStatus());
        return new LoginResponse(access, refresh, tokenProvider.getAccessTtlSeconds(), brief);
    }
}
