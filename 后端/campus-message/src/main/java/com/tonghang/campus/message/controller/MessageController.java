package com.tonghang.campus.message.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息与通知接口（M3）。
 *
 * <p>来源：《13-API接口文档》§3.3：GET /messages、PUT /messages/read-all、
 * GET /messages/badge、GET/PUT /messages/settings。
 */
@Tag(name = "M3 消息与通知")
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** GET /messages?box=&biz=&page=&size= —— 通知列表（FR-M3-01） */
    @Operation(summary = "通知列表（box=all/unread，biz=activity/competition/trade/buddy/system）")
    @RequireRole(RequireRole.USER)
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "box", required = false) String box,
            @RequestParam(value = "biz", required = false) String biz,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(messageService.list(UserContext.requireLogin(), box, biz, page, size));
    }

    /** PUT /messages/read-all —— 全部已读（FR-M3-02） */
    @Operation(summary = "全部已读（可按 biz 限定分类）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/read-all")
    public Result<Map<String, Object>> readAll(
            @RequestParam(value = "biz", required = false) String biz) {
        return Result.ok(messageService.readAll(UserContext.requireLogin(), biz));
    }

    /** PUT /messages/{id}/read —— 单条已读 */
    @Operation(summary = "单条消息已读")
    @RequireRole(RequireRole.USER)
    @PutMapping("/{id}/read")
    public Result<Map<String, Object>> read(@PathVariable("id") Long id) {
        return Result.ok(messageService.read(UserContext.requireLogin(), id));
    }

    /** GET /messages/badge —— 各分类未读数（FR-M3-08） */
    @Operation(summary = "各分类未读数（Tab 角标聚合）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/badge")
    public Result<Map<String, Object>> badge() {
        return Result.ok(messageService.badge(UserContext.requireLogin()));
    }

    /** GET /messages/settings —— 消息设置（FR-M3-02） */
    @Operation(summary = "消息分类开关 + 免打扰设置")
    @RequireRole(RequireRole.USER)
    @GetMapping("/settings")
    public Result<Map<String, Object>> getSettings() {
        return Result.ok(messageService.getSettings(UserContext.requireLogin()));
    }

    /** PUT /messages/settings —— 更新消息设置（FR-M3-02） */
    @Operation(summary = "更新消息分类开关 + 免打扰设置")
    @RequireRole(RequireRole.USER)
    @PutMapping("/settings")
    public Result<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body) {
        return Result.ok(messageService.updateSettings(UserContext.requireLogin(), body));
    }
}
