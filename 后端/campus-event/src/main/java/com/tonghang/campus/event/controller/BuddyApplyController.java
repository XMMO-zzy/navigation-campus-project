package com.tonghang.campus.event.controller;

import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.event.service.BuddyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 搭子申请处理接口（M6，契约缺口实现）。
 *
 * <p>契约缺口登记：《13-API接口文档》§3.6 只定义了「POST /buddy-posts/{id}/apply」，
 * 未定义处理申请的写入接口，但 FR-M6-04「对方同意后解锁联系方式」必须有审批入口。
 *
 * <p>处理方式：按最小语义提供 {@code PUT /buddy-applies/{id}}（action=agree|reject|withdraw），
 * 仅使用 buddy_apply 表内已有列，不新增字段；交付说明中登记为待双端评审项。
 */
@Tag(name = "M6 搭子申请处理")
@RestController
@RequestMapping("/buddy-applies")
public class BuddyApplyController {

    private final BuddyService buddyService;

    public BuddyApplyController(BuddyService buddyService) {
        this.buddyService = buddyService;
    }

    /** PUT /buddy-applies/{id} —— 处理搭子申请（契约缺口实现，待评审） */
    @Operation(summary = "处理搭子申请（action=agree|reject|withdraw）[契约缺口实现]")
    @RequireRole(RequireRole.USER)
    @PutMapping("/{id}")
    public Result<Map<String, Object>> handle(@PathVariable("id") Long id,
                                              @RequestBody Map<String, Object> body) {
        return Result.ok(buddyService.handleApply(UserContext.requireLogin(), id, body));
    }
}
