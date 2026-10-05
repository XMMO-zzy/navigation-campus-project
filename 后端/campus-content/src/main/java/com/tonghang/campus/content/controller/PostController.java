package com.tonghang.campus.content.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.common.util.IdempotentService;
import com.tonghang.campus.content.dto.FeedItem;
import com.tonghang.campus.content.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 动态接口（M2）。
 *
 * <p>来源：《13-API接口文档》§3.2：POST /posts、GET /posts/{id}、PUT|DELETE /posts/{id}、
 * POST|DELETE /posts/{id}/like、POST|DELETE /posts/{id}/favorite、
 * GET|POST /posts/{id}/comments、PUT /posts/{id}/dislike。
 */
@Tag(name = "M2 动态")
@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;
    private final IdempotentService idempotent;

    public PostController(PostService postService, IdempotentService idempotent) {
        this.postService = postService;
        this.idempotent = idempotent;
    }

    /** POST /posts —— 发布动态（FR-M2-04） */
    @Operation(summary = "发布动态（走敏感词机审）")
    @RequireRole(RequireRole.USER)
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(postService.create(uid, body));
    }

    /** GET /posts/{id} —— 动态详情（FR-M2-02） */
    @Operation(summary = "动态详情")
    @GetMapping("/{id}")
    public Result<FeedItem> detail(@PathVariable("id") Long id) {
        return Result.ok(postService.detail(UserContext.userIdOrNull(), id));
    }

    /** PUT /posts/{id} —— 编辑（FR-M7-07，仅本人） */
    @Operation(summary = "编辑动态（仅本人）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable("id") Long id,
                                              @RequestBody Map<String, Object> body) {
        return Result.ok(postService.update(UserContext.requireLogin(), id, body));
    }

    /** DELETE /posts/{id} —— 删除（软删，仅本人） */
    @Operation(summary = "删除动态（软删，仅本人）")
    @RequireRole(RequireRole.USER)
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Long id) {
        postService.remove(UserContext.requireLogin(), id);
        return Result.ok();
    }

    /* ---------------- 点赞 ---------------- */

    @Operation(summary = "点赞动态")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable("id") Long id) {
        return Result.ok(postService.like(UserContext.requireLogin(), id));
    }

    @Operation(summary = "取消点赞")
    @RequireRole(RequireRole.USER)
    @DeleteMapping("/{id}/like")
    public Result<Map<String, Object>> unlike(@PathVariable("id") Long id) {
        return Result.ok(postService.unlike(UserContext.requireLogin(), id));
    }

    /* ---------------- 收藏 ---------------- */

    @Operation(summary = "收藏动态")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable("id") Long id) {
        return Result.ok(postService.favorite(UserContext.requireLogin(), id));
    }

    @Operation(summary = "取消收藏")
    @RequireRole(RequireRole.USER)
    @DeleteMapping("/{id}/favorite")
    public Result<Map<String, Object>> unfavorite(@PathVariable("id") Long id) {
        return Result.ok(postService.unfavorite(UserContext.requireLogin(), id));
    }

    /* ---------------- 评论 ---------------- */

    @Operation(summary = "评论列表")
    @GetMapping("/{id}/comments")
    public Result<PageResult<Map<String, Object>>> comments(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(postService.comments(UserContext.userIdOrNull(), id, page, size));
    }

    @Operation(summary = "发表评论（校验评论权限）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{id}/comments")
    public Result<Map<String, Object>> comment(@PathVariable("id") Long id,
                                               @RequestBody Map<String, Object> body) {
        return Result.ok(postService.comment(UserContext.requireLogin(), id, body));
    }

    /* ---------------- 负反馈 ---------------- */

    @Operation(summary = "不感兴趣（负反馈入画像）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/{id}/dislike")
    public Result<Map<String, Object>> dislike(@PathVariable("id") Long id) {
        return Result.ok(postService.dislike(UserContext.requireLogin(), id));
    }
}
