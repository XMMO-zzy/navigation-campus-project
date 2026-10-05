package com.tonghang.campus.account.controller;

import com.tonghang.campus.account.dto.MeProfileResponse;
import com.tonghang.campus.account.dto.UserProfileResponse;
import com.tonghang.campus.account.service.AuthService;
import com.tonghang.campus.account.service.ProfileService;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户资料 / 隐私 / 黑名单接口（M1 + M7）。
 *
 * <p>来源：《13-API接口文档》§3.1（GET/PUT /users/me、GET /users/{id}、POST /users/logout、/users/deactivate）
 * 与 §3.7（GET/PUT /users/me/privacy、GET/PUT /users/me/blacklist）。
 *
 * <p>路径顺序说明：{@code /users/me/**} 固定段必须在 {@code /users/{id}} 之前声明，
 * Spring MVC 优先匹配具体路径，故此处按语义分组。
 */
@Tag(name = "M1/M7 用户资料 · 隐私")
@RestController
@RequestMapping("/users")
public class UserController {

    private final ProfileService profileService;
    private final AuthService authService;

    public UserController(ProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    /* ==================== 我的主页 ==================== */

    /** GET /users/me —— 我的主页头部（FR-M7-07） */
    @Operation(summary = "我的主页头部：资料卡 + 数据概览 + 认证状态")
    @RequireRole(RequireRole.USER)
    @GetMapping("/me")
    public Result<MeProfileResponse> me() {
        return Result.ok(profileService.me(UserContext.requireLogin()));
    }

    /** PUT /users/me —— 编辑资料（FR-M7-07） */
    @Operation(summary = "编辑资料（头像/昵称/简介/专业/技能/爱好）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/me")
    public Result<MeProfileResponse> updateMe(@RequestBody Map<String, Object> body) {
        return Result.ok(profileService.updateMe(UserContext.requireLogin(), body));
    }

    /* ==================== 隐私设置（FR-M7-06） ==================== */

    @Operation(summary = "隐私设置：资料/动态可见范围、联系权限、个性化推荐开关")
    @RequireRole(RequireRole.USER)
    @GetMapping("/me/privacy")
    public Result<Map<String, Object>> getPrivacy() {
        return Result.ok(profileService.getPrivacy(UserContext.requireLogin()));
    }

    @Operation(summary = "更新隐私设置")
    @RequireRole(RequireRole.USER)
    @PutMapping("/me/privacy")
    public Result<Map<String, Object>> updatePrivacy(@RequestBody Map<String, Object> body) {
        return Result.ok(profileService.updatePrivacy(UserContext.requireLogin(), body));
    }

    /* ==================== 黑名单（FR-M7-06 / FR-M2-11） ==================== */

    @Operation(summary = "黑名单列表")
    @RequireRole(RequireRole.USER)
    @GetMapping("/me/blacklist")
    public Result<Map<String, Object>> getBlacklist() {
        return Result.ok(profileService.getBlacklist(UserContext.requireLogin()));
    }

    @Operation(summary = "黑名单管理（action=block/unblock）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/me/blacklist")
    public Result<Map<String, Object>> updateBlacklist(@RequestBody Map<String, Object> body) {
        return Result.ok(profileService.updateBlacklist(UserContext.requireLogin(), body));
    }

    /* ==================== 账号操作 ==================== */

    /** POST /users/logout —— 退出登录（FR-M1-07） */
    @Operation(summary = "退出登录（清除服务端会话）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.requireLogin());
        return Result.ok();
    }

    /** POST /users/deactivate —— 账号注销（FR-M1-08，冷静期 15 天） */
    @Operation(summary = "账号注销（冷静期 15 天）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/deactivate")
    public Result<Map<String, Object>> deactivate(@RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(authService.deactivate(UserContext.requireLogin()));
    }

    /* ==================== 他人主页（FR-M2-11） ==================== */

    /** GET /users/{id} —— 他人主页（受隐私设置约束） */
    @Operation(summary = "他人主页（受隐私设置约束）")
    @GetMapping("/{id}")
    public Result<UserProfileResponse> other(@PathVariable("id") Long id) {
        return Result.ok(profileService.other(UserContext.userIdOrNull(), id));
    }

    /* ==================== 屏蔽（FR-M2-11 快捷入口） ==================== */

    /**
     * POST /users/{id}/block —— 屏蔽（FR-M2-11）。
     * <p>与 PUT /users/me/blacklist 等价，此路径为《13》§3.2 中的快捷写法。
     */
    @Operation(summary = "屏蔽用户")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{id}/block")
    public Result<Map<String, Object>> block(@PathVariable("id") Long id) {
        return Result.ok(profileService.updateBlacklist(UserContext.requireLogin(), blockBody(id, "block")));
    }

    /** DELETE /users/{id}/block —— 取消屏蔽（FR-M2-11） */
    @Operation(summary = "取消屏蔽")
    @RequireRole(RequireRole.USER)
    @DeleteMapping("/{id}/block")
    public Result<Map<String, Object>> unblock(@PathVariable("id") Long id) {
        return Result.ok(profileService.updateBlacklist(UserContext.requireLogin(), blockBody(id, "unblock")));
    }

    private Map<String, Object> blockBody(Long id, String action) {
        Map<String, Object> m = new java.util.LinkedHashMap<>(2);
        m.put("userId", id);
        m.put("action", action);
        return m;
    }
}
