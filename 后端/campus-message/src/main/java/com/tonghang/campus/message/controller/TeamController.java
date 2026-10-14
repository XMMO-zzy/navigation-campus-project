package com.tonghang.campus.message.controller;

import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.message.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 申请中心与队伍接口（M3）。
 *
 * <p>来源：《13-API接口文档》§3.3：GET /team-applies、PUT /team-applies/{id}、
 * POST /teams、GET /teams/{id}、PUT /teams/{id}/members/{uid}。
 */
@Tag(name = "M3 组队与申请中心")
@RestController
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    /* ==================== 申请中心（FR-M3-04） ==================== */

    /** GET /team-applies?direction= —— 申请中心 */
    @Operation(summary = "申请中心（direction=received/sent/invited）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/team-applies")
    public Result<PageResult<Map<String, Object>>> applies(
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(teamService.applies(UserContext.requireLogin(), direction, page, size));
    }

    /** PUT /team-applies/{id} —— 处理申请 */
    @Operation(summary = "处理申请（action=agree/reject/ask/withdraw）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/team-applies/{id}")
    public Result<Map<String, Object>> handleApply(@PathVariable("id") Long id,
                                                   @RequestBody Map<String, Object> body) {
        String action = body == null ? null : (body.get("action") == null
                ? null : String.valueOf(body.get("action")));
        String message = body == null ? null : (body.get("message") == null
                ? null : String.valueOf(body.get("message")));
        return Result.ok(teamService.handleApply(UserContext.requireLogin(), id, action, message));
    }

    /* ==================== 队伍 ==================== */

    /** POST /teams —— 创建队伍（US-03a） */
    @Operation(summary = "创建队伍（活动/竞赛组队）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/teams")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.ok(teamService.create(UserContext.requireLogin(), body));
    }

    /** GET /teams/{id} —— 队伍详情 */
    @Operation(summary = "队伍详情（成员/分工/公告/进度）")
    @GetMapping("/teams/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(teamService.detail(UserContext.userIdOrNull(), id));
    }

    /** GET /teams/my —— 我参与的队伍（组队入口用；契约缺口登记项） */
    @Operation(summary = "我参与的队伍列表")
    @RequireRole(RequireRole.USER)
    @GetMapping("/teams/my")
    public Result<java.util.List<Map<String, Object>>> myTeams() {
        return Result.ok(teamService.myTeams(UserContext.requireLogin()));
    }

    /** POST /teams/{id}/apply —— 申请加入队伍（契约缺口登记项，见交付说明） */
    @Operation(summary = "申请加入队伍")
    @RequireRole(RequireRole.USER)
    @PostMapping("/teams/{id}/apply")
    public Result<Map<String, Object>> apply(@PathVariable("id") Long id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(teamService.apply(UserContext.requireLogin(), id, body));
    }

    /** PUT /teams/{id}/members/{uid} —— 移除成员 / 转让队长（FR-M5-01） */
    @Operation(summary = "移除成员 / 转让队长 / 设置分工（action=remove/transfer/setDuty）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/teams/{id}/members/{uid}")
    public Result<Map<String, Object>> updateMember(@PathVariable("id") Long teamId,
                                                    @PathVariable("uid") Long uid,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(teamService.updateMember(UserContext.requireLogin(), teamId, uid, body));
    }

    /**
     * GET /im/sign —— 获取 TIM userSig（FR-M3-03）。
     *
     * <p>说明：腾讯云 IM SDK 未接入（application.yml 的 campus.im 为空），
     * 按契约缺口处理——返回明确错误而非臆造 userSig。
     */
    @Operation(summary = "获取 TIM userSig（私信/群聊通道，SDK 未接入时返回 50001）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/im/sign")
    public Result<Map<String, Object>> imSign() {
        throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "IM 私信通道尚未接入，请联系管理员开通");
    }
}
