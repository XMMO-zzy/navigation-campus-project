package com.tonghang.campus.modules.m7.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.common.util.IdempotentService;
import com.tonghang.campus.modules.m7.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 举报接口（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7：
 * POST /reports（◆ 核心接口，24h SLA）、GET /reports/my。
 */
@Tag(name = "M7 举报")
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final IdempotentService idempotent;

    public ReportController(ReportService reportService, IdempotentService idempotent) {
        this.reportService = reportService;
        this.idempotent = idempotent;
    }

    /** POST /reports —— 举报（FR-M7-03） */
    @Operation(summary = "举报（原因 + 证据材料，响应含 slaHours=24）")
    @RequireRole(RequireRole.USER)
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(reportService.create(uid, body));
    }

    /** GET /reports/my —— 我的举报记录（FR-M7-06） */
    @Operation(summary = "我的举报记录（进度/结果/申诉入口）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/my")
    public Result<PageResult<Map<String, Object>>> my(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(reportService.my(UserContext.requireLogin(), page, size));
    }
}
