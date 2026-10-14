package com.tonghang.campus.content.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.content.dto.FeedItem;
import com.tonghang.campus.content.service.FeedService;
import com.tonghang.campus.content.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 信息流与搜索接口（M2）。
 *
 * <p>来源：《13-API接口文档》§3.2：
 * <ul>
 *   <li>GET /feed 动态页信息流</li>
 *   <li>GET /home/feeds 首页推荐流</li>
 *   <li>GET /search 全局搜索 ｜ GET /search/history ｜ DELETE /search/history ｜ GET /search/hot</li>
 * </ul>
 *
 * <p>{@code /todos} 由 M3（重要消息待办，FR-M3-05）实现，见 message 模块的 TodoController。
 */
@Tag(name = "M2 内容与信息流")
@RestController
public class FeedController {

    private final FeedService feedService;
    private final SearchService searchService;

    public FeedController(FeedService feedService, SearchService searchService) {
        this.feedService = feedService;
        this.searchService = searchService;
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

    /* ==================== 搜索（FR-M2-07） ==================== */

    /**
     * GET /search?kw=&amp;type= —— 全局搜索。
     *
     * <p>游客可搜（写入热词计数）；登录用户额外落个人搜索历史。
     */
    @Operation(summary = "全局搜索（type= 空/综合、post 动态、topic 话题、notice 公告、message 消息）")
    @GetMapping("/search")
    public Result<PageResult<Map<String, Object>>> search(
            @RequestParam("kw") String kw,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(searchService.search(UserContext.userIdOrNull(), kw, type, page, size));
    }

    /**
     * GET /search/history —— 搜索历史（FR-M2-07）。
     *
     * <p>存储：Redis（《12》无 search_history 表；与同节 /search/hot 的 Redis 口径一致）。
     */
    @Operation(summary = "搜索历史（Redis，上限 20 条）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/search/history")
    public Result<List<Map<String, Object>>> searchHistory() {
        return Result.ok(searchService.history(UserContext.requireLogin()));
    }

    /**
     * DELETE /search/history —— 删除搜索历史（FR-M2-07）。
     *
     * <p>带 {@code id} 删除单条；不带则一键清空（前端 clearHistory 即不传参）。
     */
    @Operation(summary = "删除搜索历史（带 id 删单条；不带则清空全部）")
    @RequireRole(RequireRole.USER)
    @DeleteMapping("/search/history")
    public Result<Map<String, Object>> deleteSearchHistory(
            @RequestParam(value = "id", required = false) String id) {
        Long uid = UserContext.requireLogin();
        if (id == null || id.trim().isEmpty()) {
            return Result.ok(searchService.clearHistory(uid));
        }
        return Result.ok(searchService.removeHistory(uid, id));
    }

    /** GET /search/hot —— 热门搜索词（FR-M2-07，Redis ZSet Top10） */
    @Operation(summary = "热门搜索词（Redis ZSet，Top10）")
    @GetMapping("/search/hot")
    public Result<List<Map<String, Object>>> searchHot() {
        return Result.ok(searchService.hot());
    }
}
