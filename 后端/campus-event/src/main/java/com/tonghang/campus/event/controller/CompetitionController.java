package com.tonghang.campus.event.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.common.util.IdempotentService;
import com.tonghang.campus.event.service.CompetitionService;
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
 * 竞赛接口（M5）。
 *
 * <p>来源：《13-API接口文档》§3.5：
 * POST /competitions、GET /competitions、GET /competitions/calendar?month=、
 * POST /competitions/{id}/enroll、GET /competitions/my-enrolls。
 *
 * <p>权限：发布竞赛 = {@code @RequireRole(VERIFIED)}；报名 = 登录即可。
 */
@Tag(name = "M5 竞赛")
@RestController
@RequestMapping("/competitions")
public class CompetitionController {

    private final CompetitionService competitionService;
    private final IdempotentService idempotent;

    public CompetitionController(CompetitionService competitionService, IdempotentService idempotent) {
        this.competitionService = competitionService;
        this.idempotent = idempotent;
    }

    /** POST /competitions —— 发布竞赛（FR-M5-03） */
    @Operation(summary = "发布竞赛（仅认证用户）")
    @RequireRole(RequireRole.VERIFIED)
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireVerified();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(competitionService.create(uid, body));
    }

    /**
     * GET /competitions/calendar?month=yyyy-MM —— 竞赛日程日历（FR-M5-04）。
     *
     * <p>注意：必须声明在 {@code /{id}} 之前，否则会被路径变量吞掉。
     */
    @Operation(summary = "竞赛日程日历（报名/初赛/决赛/截止）")
    @GetMapping("/calendar")
    public Result<Map<String, Object>> calendar(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "level", required = false) Integer level) {
        return Result.ok(competitionService.calendar(month, level));
    }

    /** GET /competitions/my-enrolls —— 我的参赛记录 + 成果（FR-M7-07） */
    @Operation(summary = "我的参赛记录 + 成果展示")
    @RequireRole(RequireRole.USER)
    @GetMapping("/my-enrolls")
    public Result<PageResult<Map<String, Object>>> myEnrolls(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(competitionService.myEnrolls(UserContext.requireLogin(), page, size));
    }

    /** GET /competitions?level=&status= —— 竞赛列表 + 多维筛选（FR-M5-04） */
    @Operation(summary = "竞赛列表（级别/状态筛选）")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "kw", required = false) String kw,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(competitionService.list(UserContext.userIdOrNull(), level, status, kw, page, size));
    }

    /** GET /competitions/{id} —— 竞赛详情（列表跳转需要，《13》未单列，按同规则提供） */
    @Operation(summary = "竞赛详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(competitionService.detail(UserContext.userIdOrNull(), id));
    }

    /**
     * POST /competitions/{id}/enroll —— 报名竞赛（FR-M5-05，可关联 team_id）。
     *
     * <p>重复报名 → 40006；未开始/已截止/人数已满 → 40007。
     */
    @Operation(summary = "报名竞赛（可关联 team_id）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{id}/enroll")
    public Result<Map<String, Object>> enroll(@PathVariable("id") Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(competitionService.enroll(uid, id, body));
    }
}
