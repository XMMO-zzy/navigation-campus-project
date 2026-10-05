package com.tonghang.campus.content.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.content.dto.FeedItem;
import com.tonghang.campus.content.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 信息流接口（M2）。
 *
 * <p>来源：《13-API接口文档》§3.2：GET /feed、GET /home/feeds、GET /todos。
 * <p>{@code /todos} 由 M3（重要消息待办，FR-M3-05）实现，见 message 模块。
 */
@Tag(name = "M2 内容与信息流")
@RestController
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /** GET /feed?tab=&page=&size= —— 动态页信息流（FR-M2-01/02） */
    @Operation(summary = "动态页信息流（tab=recommend/follow/activity/campus）")
    @GetMapping("/feed")
    public Result<PageResult<FeedItem>> feed(
            @RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(feedService.feed(UserContext.userIdOrNull(), tab, page, size));
    }

    /** GET /home/feeds?page=&size= —— 首页推荐流（FR-M2-08/09） */
    @Operation(summary = "首页推荐流（机会推荐 + 信息推荐）")
    @GetMapping("/home/feeds")
    public Result<Map<String, Object>> homeFeeds(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(feedService.homeFeeds(UserContext.userIdOrNull(), page, size));
    }

    /**
     * GET /todos —— 首页重要消息待办（FR-M3-05）。
     *
     * <p>实现归 M3；此处保留入口以便前端按《13》§3.2 调用，
     * 具体聚合由 message 模块的 TodoController 承载（同为 /todos 路径，只注册一处）。
     */

    /** GET /search?kw=&type= —— 全局搜索（FR-M2-07） */
    @Operation(summary = "全局搜索（动态/帖子/重要消息/校园话题）")
    @GetMapping("/search")
    public Result<PageResult<Map<String, Object>>> search(
            @RequestParam("kw") String kw,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(feedService.search(UserContext.userIdOrNull(), kw, type, page, size));
    }

    /** GET /search/history —— 搜索历史（FR-M2-07） */
    @Operation(summary = "搜索历史")
    @RequireRole(RequireRole.USER)
    @GetMapping("/search/history")
    public Result<java.util.List<Map<String, Object>>> searchHistory() {
        return Result.ok(feedService.searchHistory(UserContext.requireLogin()));
    }
}
