package com.tonghang.campus.event.controller;

import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.event.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 报名审核接口（M5）。
 *
 * <p>来源：《13-API接口文档》§3.5「PUT /enrolls/{id} 组织者审核报名：approve/reject」。
 *
 * <p>路径为 {@code /enrolls/{id}}（不带 activities 前缀），故单独成类挂载。
 * 权限：仅活动组织者（发布者）可操作，服务层二次校验（《11》§3.1 认证用户）。
 */
@Tag(name = "M5 报名审核")
@RestController
@RequestMapping("/enrolls")
public class EnrollController {

    private final ActivityService activityService;

    public EnrollController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** PUT /enrolls/{id} —— 组织者审核报名（FR-M5-02） */
    @Operation(summary = "组织者审核报名（action=approve|reject）")
    @RequireRole(RequireRole.VERIFIED)
    @PutMapping("/{id}")
    public Result<Map<String, Object>> handle(@PathVariable("id") Long id,
                                              @RequestBody Map<String, Object> body) {
        return Result.ok(activityService.handleEnroll(UserContext.requireVerified(), id, body));
    }
}
