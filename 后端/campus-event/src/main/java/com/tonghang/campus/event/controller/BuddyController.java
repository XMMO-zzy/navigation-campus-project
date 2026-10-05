package com.tonghang.campus.event.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.common.util.IdempotentService;
import com.tonghang.campus.event.service.BuddyService;
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
 * 找搭子接口（M6）。
 *
 * <p>来源：《13-API接口文档》§3.6：
 * POST /buddy-posts、GET /buddy-posts?type=&amp;tag=&amp;femaleOnly=、
 * GET /buddy-posts/my?view=published|favorite|matched、
 * POST /buddy-posts/{id}/apply。
 *
 * <p>权限（《10》§3.1）：发布搭子帖 / 申请加入 = 登录即可（普通用户可用）。
 */
@Tag(name = "M6 找搭子")
@RestController
@RequestMapping("/buddy-posts")
public class BuddyController {

    private final BuddyService buddyService;
    private final IdempotentService idempotent;

    public BuddyController(BuddyService buddyService, IdempotentService idempotent) {
        this.buddyService = buddyService;
        this.idempotent = idempotent;
    }

    /** POST /buddy-posts —— 发布搭子帖（FR-M6-01） */
    @Operation(summary = "发布搭子帖（类型/标签/展示信息/gender_limit）")
    @RequireRole(RequireRole.USER)
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(buddyService.create(uid, body));
    }

    /**
     * GET /buddy-posts/my?view= —— 我的搭子帖（FR-M6-03）。
     *
     * <p>注意：必须声明在 {@code /{id}} 之前，否则会被路径变量吞掉。
     */
    @Operation(summary = "我的搭子帖（published/favorite/matched）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/my")
    public Result<PageResult<Map<String, Object>>> my(
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(buddyService.my(UserContext.requireLogin(), view, page, size));
    }

    /** GET /buddy-posts?type=&tag=&femaleOnly= —— 搭子列表（FR-M6-02/05） */
    @Operation(summary = "搭子列表（爱好筛选、女性模式）")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "femaleOnly", required = false) Boolean femaleOnly,
            @RequestParam(value = "kw", required = false) String kw,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(buddyService.list(UserContext.userIdOrNull(), type, tag, femaleOnly, kw, page, size));
    }

    /** GET /buddy-posts/{id} —— 搭子详情（含发布者可见的申请列表） */
    @Operation(summary = "搭子详情（联系方式按解锁状态脱敏）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(buddyService.detail(UserContext.userIdOrNull(), id));
    }

    /** POST /buddy-posts/{id}/apply —— 申请加入（FR-M6-04，重复 40006） */
    @Operation(summary = "申请加入搭子（对方同意后解锁联系方式）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{id}/apply")
    public Result<Map<String, Object>> apply(@PathVariable("id") Long id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(buddyService.apply(uid, id, body));
    }
}
