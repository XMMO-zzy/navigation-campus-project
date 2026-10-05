package com.tonghang.campus.modules.m7.controller;

import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.modules.m7.service.DraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 草稿接口（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7「GET/PUT /drafts/{type} 草稿读取/保存（五类，JSON payload）」
 * — FR-M2-10。
 *
 * <p>type 取值：1 动态 / 2 商品 / 3 活动 / 4 竞赛 / 5 搭子（《12》§2.7 draft.draft_type）。
 */
@Tag(name = "M7 草稿")
@RestController
@RequestMapping("/drafts")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    /** GET /drafts/{type} —— 读取草稿（无草稿时 payload 为 null） */
    @Operation(summary = "读取草稿（type: 1 动态 / 2 商品 / 3 活动 / 4 竞赛 / 5 搭子）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/{type}")
    public Result<Map<String, Object>> get(@PathVariable("type") Integer type) {
        return Result.ok(draftService.get(UserContext.requireLogin(), type));
    }

    /** PUT /drafts/{type} —— 保存草稿（按 user_id + draft_type 唯一键 upsert） */
    @Operation(summary = "保存草稿（JSON payload，自动 upsert）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/{type}")
    public Result<Map<String, Object>> save(@PathVariable("type") Integer type,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Object payload = body == null ? null : body.get("payload");
        return Result.ok(draftService.save(UserContext.requireLogin(), type, payload));
    }

    /** DELETE /drafts/{type} —— 清理草稿（发布成功后调用；《13》未单列，作为补充能力） */
    @Operation(summary = "清理草稿（发布成功后调用）")
    @RequireRole(RequireRole.USER)
    @org.springframework.web.bind.annotation.DeleteMapping("/{type}")
    public Result<Map<String, Object>> remove(@PathVariable("type") Integer type) {
        return Result.ok(draftService.remove(UserContext.requireLogin(), type));
    }
}
