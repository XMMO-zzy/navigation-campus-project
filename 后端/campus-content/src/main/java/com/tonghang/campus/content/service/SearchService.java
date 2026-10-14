package com.tonghang.campus.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.redis.RedisKeys;
import com.tonghang.campus.common.redis.RedisSupport;
import com.tonghang.campus.common.search.MessageSearchFacade;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.dto.FeedItem;
import com.tonghang.campus.content.entity.OfficialNotice;
import com.tonghang.campus.content.entity.Post;
import com.tonghang.campus.content.entity.Topic;
import com.tonghang.campus.content.mapper.OfficialNoticeMapper;
import com.tonghang.campus.content.mapper.PostMapper;
import com.tonghang.campus.content.mapper.TopicMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全局搜索与搜索辅助服务（M2）。
 *
 * <p>来源：《13-API接口文档》§3.2 /《10》FR-M2-07：
 * <ul>
 *   <li>{@code GET /search?kw=&type=} 全局搜索（动态/帖子/重要消息/校园话题）</li>
 *   <li>{@code GET/DELETE /search/history} 搜索历史（单条删除 <code>?id=</code> + 一键清空）</li>
 *   <li>{@code GET /search/hot} 热门搜索词（Redis ZSet）</li>
 * </ul>
 *
 * <p><b>存储口径（已与用户确认：不改《12》表结构，用 Redis + 本地存储）</b>：
 * 《12》未建 search_history 表，而《13》§3.2 已注明热门搜索走 Redis ZSet，
 * 故搜索历史同样落 Redis（per-user List，上限 {@value RedisKeys#SEARCH_HISTORY_MAX} 条，TTL 30 天），
 * 前端另存一份本地副本作为 Redis 不可用时的兜底展示。
 *
 * <p><b>type 口径</b>（前端 pages/search 的页签与该取值一一对应）：
 * <pre>
 *   ''        综合：动态分页 + 公告（仅第 1 页附带最多 3 条，避免分页语义被打破）
 *   post      动态
 *   topic     话题
 *   notice    官方公告
 *   message   我的消息/待办（跨模块，走 MessageSearchFacade）
 * </pre>
 */
@Service
public class SearchService {

    /** 综合搜索结果里附带的最大公告条数 */
    private static final int MIXED_NOTICE_LIMIT = 3;

    /** 单次搜索最多写入的热词条目 */
    private static final int KEYWORD_MAX_LEN = 40;

    private final PostMapper postMapper;
    private final TopicMapper topicMapper;
    private final OfficialNoticeMapper noticeMapper;
    private final PostService postService;
    private final RedisSupport redis;
    private final ObjectProvider<MessageSearchFacade> messageFacadeProvider;

    public SearchService(PostMapper postMapper,
                         TopicMapper topicMapper,
                         OfficialNoticeMapper noticeMapper,
                         PostService postService,
                         RedisSupport redis,
                         ObjectProvider<MessageSearchFacade> messageFacadeProvider) {
        this.postMapper = postMapper;
        this.topicMapper = topicMapper;
        this.noticeMapper = noticeMapper;
        this.postService = postService;
        this.redis = redis;
        this.messageFacadeProvider = messageFacadeProvider;
    }

    /* ==================== 全局搜索 ==================== */

    /**
     * 全局搜索（GET /search?kw=&amp;type=）。
     *
     * <p>副作用（埋点，FR-M2-07 热门搜索的数据来源）：
     * 每次有效搜索都会给热词 ZSet 计数 +1；已登录用户同时写入其个人搜索历史。
     */
    public PageResult<Map<String, Object>> search(Long viewerId, String kw, String type,
                                                 Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        if (kw == null || kw.trim().isEmpty()) {
            throw BizException.param("请输入搜索关键词");
        }
        String keyword = kw.trim();
        String t = type == null ? "" : type.trim();

        // 埋点：热词（游客也计）+ 个人历史（仅登录用户）
        recordHotKeyword(keyword);
        if (viewerId != null) {
            pushHistory(viewerId, keyword);
        }

        switch (t) {
            case "post":
                return searchPosts(viewerId, keyword, p, s);
            case "topic":
                return searchTopics(keyword, p, s);
            case "notice":
                return searchNotices(keyword, p, s);
            case "message":
                return searchMessages(viewerId, keyword, p, s);
            case "":
            default:
                return searchMixed(viewerId, keyword, p, s);
        }
    }

    /** 综合：动态分页 + 首页附带少量公告 */
    private PageResult<Map<String, Object>> searchMixed(Long viewerId, String keyword, int p, int s) {
        PageResult<Map<String, Object>> postResult = searchPosts(viewerId, keyword, p, s);
        if (p > 1) {
            return postResult;
        }
        List<OfficialNotice> notices = noticeMapper.selectList(new LambdaQueryWrapper<OfficialNotice>()
                .and(w -> w.like(OfficialNotice::getTitle, keyword)
                        .or().like(OfficialNotice::getContent, keyword))
                .orderByDesc(OfficialNotice::getCreatedAt)
                .last("LIMIT " + MIXED_NOTICE_LIMIT));

        List<Map<String, Object>> merged = new ArrayList<>();
        for (OfficialNotice n : notices) {
            merged.add(noticeItem(n));
        }
        merged.addAll(postResult.getList());
        return PageResult.of(merged, postResult.getTotal() + notices.size(), p, s);
    }

    /** 动态检索：正文 LIKE（《13》§3.2）；可见性沿用统一规则（含屏蔽过滤） */
    private PageResult<Map<String, Object>> searchPosts(Long viewerId, String keyword, int p, int s) {
        Page<Post> pageObj = new Page<>(p, s);
        postMapper.selectPage(pageObj, postService.visibleWrapper(viewerId)
                .like(Post::getContent, keyword)
                .orderByDesc(Post::getCreatedAt));

        List<Map<String, Object>> list = new ArrayList<>();
        for (FeedItem item : postService.toFeedItems(viewerId, pageObj.getRecords())) {
            Map<String, Object> m = new LinkedHashMap<>(8);
            m.put("contentType", FeedItem.TYPE_POST);
            m.put("contentId", item.getContentId());
            m.put("title", item.getTitle());
            m.put("text", item.getText());
            m.put("images", item.getImages());
            m.put("user", item.getUser());
            m.put("createdAt", item.getCreatedAt());
            list.add(m);
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /** 话题检索（《12》§2.2 topic：name / type / use_count） */
    private PageResult<Map<String, Object>> searchTopics(String keyword, int p, int s) {
        Page<Topic> pageObj = new Page<>(p, s);
        topicMapper.selectPage(pageObj, new LambdaQueryWrapper<Topic>()
                .like(Topic::getName, keyword)
                .orderByDesc(Topic::getUseCount));

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (Topic t : pageObj.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>(6);
            m.put("contentType", 9);            // 话题（前端 constants.CONTENT_TYPE.TOPIC = 9）
            m.put("contentId", t.getId());
            m.put("title", t.getName());
            m.put("topicType", t.getType());
            m.put("useCount", t.getUseCount());
            m.put("createdAt", t.getCreatedAt());
            list.add(m);
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /** 公告检索 */
    private PageResult<Map<String, Object>> searchNotices(String keyword, int p, int s) {
        Page<OfficialNotice> pageObj = new Page<>(p, s);
        noticeMapper.selectPage(pageObj, new LambdaQueryWrapper<OfficialNotice>()
                .and(w -> w.like(OfficialNotice::getTitle, keyword)
                        .or().like(OfficialNotice::getContent, keyword))
                .orderByDesc(OfficialNotice::getCreatedAt));

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (OfficialNotice n : pageObj.getRecords()) {
            list.add(noticeItem(n));
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /** 我的消息/待办检索（跨模块走门面；门面未装配时返回空，不臆造数据） */
    private PageResult<Map<String, Object>> searchMessages(Long viewerId, String keyword, int p, int s) {
        if (viewerId == null) {
            return PageResult.empty(p, s);
        }
        MessageSearchFacade facade = messageFacadeProvider.getIfAvailable();
        if (facade == null) {
            return PageResult.empty(p, s);
        }
        List<Map<String, Object>> all = facade.searchMessages(viewerId, keyword);
        int from = (p - 1) * s;
        if (all == null || from >= all.size()) {
            return PageResult.of(Collections.emptyList(), all == null ? 0 : all.size(), p, s);
        }
        int to = Math.min(from + s, all.size());
        return PageResult.of(new ArrayList<>(all.subList(from, to)), all.size(), p, s);
    }

    private Map<String, Object> noticeItem(OfficialNotice n) {
        Map<String, Object> m = new LinkedHashMap<>(6);
        m.put("contentType", FeedItem.TYPE_NOTICE);
        m.put("contentId", n.getId());
        m.put("title", n.getTitle());
        m.put("text", n.getContent());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }

    /* ==================== 搜索历史（Redis，FR-M2-07） ==================== */

    /**
     * 读取搜索历史。
     *
     * <p>返回 {@code [{id, kw}]}：{@code id} 即关键词本身（前端删除时回传该值）。
     * Redis 不可用时返回空列表，前端会退化到本地缓存展示。
     */
    public List<Map<String, Object>> history(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> kws = redis.call(t -> t.opsForList().range(RedisKeys.searchHistory(userId), 0, -1),
                Collections.<String>emptyList());
        List<Map<String, Object>> out = new ArrayList<>(kws.size());
        for (String kw : kws) {
            Map<String, Object> m = new LinkedHashMap<>(2);
            m.put("id", kw);
            m.put("kw", kw);
            out.add(m);
        }
        return out;
    }

    /** 删除单条历史（DELETE /search/history?id=） */
    public Map<String, Object> removeHistory(Long userId, String id) {
        Map<String, Object> data = new LinkedHashMap<>(2);
        if (userId == null || id == null || id.trim().isEmpty()) {
            data.put("removed", 0);
            return data;
        }
        String kw = id.trim();
        Long removed = redis.call(t -> t.opsForList().remove(RedisKeys.searchHistory(userId), 1, kw), 0L);
        data.put("removed", removed == null ? 0 : removed);
        return data;
    }

    /** 清空全部历史（DELETE /search/history） */
    public Map<String, Object> clearHistory(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>(2);
        if (userId == null) {
            data.put("cleared", false);
            return data;
        }
        redis.run(t -> t.delete(RedisKeys.searchHistory(userId)));
        data.put("cleared", true);
        return data;
    }

    /** 热门搜索词 Top N（GET /search/hot，Redis ZSet） */
    public List<Map<String, Object>> hot() {
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                redis.call(t -> t.opsForZSet().reverseRangeWithScores(
                                RedisKeys.SEARCH_HOT, 0, RedisKeys.SEARCH_HOT_TOP - 1),
                        Collections.<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>>emptySet());

        List<Map<String, Object>> out = new ArrayList<>();
        for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple == null || tuple.getValue() == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>(2);
            m.put("kw", tuple.getValue());
            m.put("count", tuple.getScore() == null ? 0 : tuple.getScore().longValue());
            out.add(m);
        }
        return out;
    }

    /* ==================== 内部：埋点 ==================== */

    /** 写入个人搜索历史：去重前置 + 截断到上限 + 续期 */
    private void pushHistory(Long userId, String keyword) {
        String key = RedisKeys.searchHistory(userId);
        redis.run(t -> {
            // 先移除同值项，保证重复搜索会置顶而不是堆叠
            t.opsForList().remove(key, 0, keyword);
            t.opsForList().leftPush(key, keyword);
            t.opsForList().trim(key, 0, RedisKeys.SEARCH_HISTORY_MAX - 1);
            t.expire(key, RedisKeys.SEARCH_HISTORY_TTL);
        });
    }

    /** 热词计数 +1（ZSet，score = 搜索次数） */
    private void recordHotKeyword(String keyword) {
        String member = keyword.length() > KEYWORD_MAX_LEN ? keyword.substring(0, KEYWORD_MAX_LEN) : keyword;
        redis.run(t -> t.opsForZSet().incrementScore(RedisKeys.SEARCH_HOT, member, 1D));
    }
}
