package com.tonghang.campus.account.controller;

import com.tonghang.campus.account.service.VerifyService;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 学号认证接口（M1）。
 *
 * <p>来源：《13-API接口文档》§3.1：POST /verify/apply、GET /verify/status。
 * 四态口径见《10》FR-M1-06（阶段二决策之①）。
 */
@Tag(name = "M1 学号认证")
@RestController
@RequestMapping("/verify")
public class VerifyController {

    private final VerifyService verifyService;

    public VerifyController(VerifyService verifyService) {
        this.verifyService = verifyService;
    }

    /** POST /verify/apply —— 提交学号认证（FR-M1-05） */
    @Operation(summary = "提交学号认证（学号+姓名+校园卡照片）→ 转为认证中")
    @RequireRole(RequireRole.USER)
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        return Result.ok(verifyService.apply(UserContext.requireLogin(), body));
    }

    /** GET /verify/status —— 认证四态 + 未通过原因（FR-M1-06） */
    @Operation(summary = "认证四态 + 未通过原因")
    @RequireRole(RequireRole.USER)
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(verifyService.status(UserContext.requireLogin()));
    }
}
