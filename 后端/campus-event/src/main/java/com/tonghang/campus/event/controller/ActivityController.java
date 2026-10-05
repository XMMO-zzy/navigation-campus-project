package com.tonghang.campus.event.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.common.util.IdempotentService;
import com.tonghang.campus.event.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 活动接口（M5）。
 *
 * <p>来源：《13-API接口文档》§3.5 M5 活动与竞赛：
 * POST /activities、GET /activities、GET /activities/{id}、POST /activities/{id}/enroll、
 * PUT /enrolls/{id}、GET /activities/my-enrolls。
 *
 * <p>权限（《11》§3.1）：发布活动 = {@code @RequireRole(VERIFIED)}；
 * 报名 = 登录即可（普通用户也可参加活动，见《10》§3.1）。
 */
@Tag(name = "M5 活动")
@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final ActivityService activityService;
    private final IdempotentService idempotent;

    public ActivityController(ActivityService activityService, IdempotentService idempotent) {
        this.activityService = activityService;
        this.idempotent = idempotent;
    }

    /**
     * POST /activities —— 发布活动（FR-M5-01）。
     *
     * <p>普通用户调用 → 40003（见《13》§4「◆ POST /activities」示例注释）。
     */
    @Operation(summary = "发布活动（仅认证用户）")
    @RequireRole(RequireRole.VERIFIED)
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireVerified();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(activityService.create(uid, body));
    }

    /** GET /activities?category=&status= —— 活动列表（FR-M5-02） */
    @Operation(summary = "活动列表（分类/状态筛选）")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "category", required = false) Integer category,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "kw", required = false) String kw,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(activityService.list(UserContext.userIdOrNull(), category, status, kw, page, size));
    }

    /** GET /activities/my-enrolls —— 我参与的活动（FR-M7-07） */
    @Operation(summary = "我参与的活动（ongoing/history）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/my-enrolls")
    public Result<PageResult<Map<String, Object>>> myEnrolls(
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(activityService.myEnrolls(UserContext.requireLogin(), view, page, size));
    }

    /** GET /activities/{id} —— 活动详情（FR-M5-02） */
    @Operation(summary = "活动详情（时间/人数/地点/要求/规则）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(activityService.detail(UserContext.userIdOrNull(), id));
    }

    /**
     * POST /activities/{id}/enroll —— 报名（FR-M5-05）。
     *
     * <p>重复报名 → 40006；报名未开始/已截止/人数已满 → 40007。
     */
    @Operation(summary = "报名活动（重复 40006、截止 40007）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{id}/enroll")
    public Result<Map<String, Object>> enroll(@PathVariable("id") Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(activityService.enroll(uid, id, body));
    }
}
