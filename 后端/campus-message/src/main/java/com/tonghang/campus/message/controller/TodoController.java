package com.tonghang.campus.message.controller;

import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.message.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首页待办接口（M3）。
 *
 * <p>来源：《13-API接口文档》§3.2 GET /todos
 * 「首页重要消息待办（即将截止/报名开始/规则更新/协作待办）」— FR-M3-05。
 */
@Tag(name = "M3 首页待办")
@RestController
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    /** GET /todos —— 首页重要消息待办（FR-M3-05） */
    @Operation(summary = "首页重要消息待办（即将截止/报名开始/规则更新/协作待办）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/todos")
    public Result<Map<String, Object>> todos() {
        return Result.ok(todoService.todos(UserContext.requireLogin()));
    }
}
