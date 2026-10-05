package com.tonghang.campus.modules.m7.controller;

import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.modules.m7.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 帮助与反馈接口（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7「GET/POST /feedback 帮助与反馈」— FR-M7-06。
 */
@Tag(name = "M7 帮助与反馈")
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** GET /feedback —— 帮助页（FAQ + 我的反馈记录） */
    @Operation(summary = "帮助与反馈页（FAQ + 我的反馈）")
    @RequireRole(RequireRole.USER)
    @GetMapping
    public Result<Map<String, Object>> page() {
        return Result.ok(feedbackService.page(UserContext.requireLogin()));
    }

    /** POST /feedback —— 提交反馈（FR-M7-06） */
    @Operation(summary = "提交意见反馈（类型/内容/图片/联系方式）")
    @RequireRole(RequireRole.USER)
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return Result.ok(feedbackService.create(UserContext.requireLogin(), body));
    }
}
