package com.tonghang.campus.account.controller;

import com.tonghang.campus.account.dto.LoginResponse;
import com.tonghang.campus.account.dto.WxLoginRequest;
import com.tonghang.campus.account.service.AuthService;
import com.tonghang.campus.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口（M1）。
 *
 * <p>来源：《13-API接口文档》§3.1：POST /auth/wx-login、/auth/phone-login、/auth/refresh。
 * <p>本控制器路径在 {@code WebMvcConfig} 中已排除登录态拦截。
 */
@Tag(name = "M1 账号与认证")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /auth/wx-login —— 微信一键授权登录（FR-M1-01） */
    @Operation(summary = "微信一键授权登录（code 换 openID，首登建号）")
    @PostMapping("/wx-login")
    public Result<LoginResponse> wxLogin(@RequestBody WxLoginRequest req) {
        return Result.ok(authService.wxLogin(req));
    }

    /** POST /auth/phone-login —— 手机号验证码登录（FR-M1-02，备用通道） */
    @Operation(summary = "手机号验证码登录（备用通道，短信通道未接入时返回 40007）")
    @PostMapping("/phone-login")
    public Result<LoginResponse> phoneLogin(@RequestBody Map<String, Object> body) {
        String phone = body == null ? null : String.valueOf(body.get("phone"));
        String smsCode = body == null ? null : String.valueOf(body.get("smsCode"));
        return Result.ok(authService.phoneLogin(phone, smsCode));
    }

    /** POST /auth/refresh —— 刷新 accessToken（FR-M1-04） */
    @Operation(summary = "刷新 accessToken")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody Map<String, Object> body) {
        String refreshToken = body == null ? null : String.valueOf(body.get("refreshToken"));
        return Result.ok(authService.refresh(refreshToken));
    }
}
