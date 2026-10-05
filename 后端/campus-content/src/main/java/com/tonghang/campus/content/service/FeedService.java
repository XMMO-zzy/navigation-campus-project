package com.tonghang.campus.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.dto.FeedItem;
import com.tonghang.campus.content.entity.Post;
import com.tonghang.campus.content.entity.PostTopicRel;
import com.tonghang.campus.content.mapper.PostMapper;
import com.tonghang.campus.content.mapper.PostTopicRelMapper;
import com.tonghang.campus.content.api.GoodsQueryFacade;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 信息流服务（M2）。
 *
 * <p>来源：《13-API接口文档》§3.2：
 * <ul>
 *   <li>GET /feed?tab= 动态页信息流：recommend / follow / activity / campus — FR-M2-01/02</li>
 *   <li>GET /home/feeds 首页推荐流（机会推荐 + 信息推荐）— FR-M2-08/09</li>
 * </ul>
 *
 * <p>页签口径（《10》FR-M2-01/02）：
 * <pre>
 *   recommend  推荐：全站公开动态，按发布时间倒序（画像接入后改为个性化排序）
 *   follow     关注：仅展示已关注用户的动态 —— 契约缺口期返回空列表 + 空态引导
 *   activity   活动：聚合活动/竞赛相关内容（有活动域 Facade 时按内容类型过滤）
 *   campus     校园：校园话题下的公开动态（topic.type = 7 校园）
 * </pre>
 */
@Service
public class FeedService {

    /** 页签：推荐 */
    public static final String TAB_RECOMMEND = "recommend";
    /** 页签：关注 */
    public static final String TAB_FOLLOW = "follow";
    /** 页签：活动 */
    public static final String TAB_ACTIVITY = "activity";
    /** 页签：校园 */
    public static final String TAB_CAMPUS = "campus";

    /** 话题类型：校园（《12》§2.2 topic.type = 7） */
    private static final int TOPIC_TYPE_CAMPUS = 7;

    private final PostMapper postMapper;
    private final PostTopicRelMapper postTopicRelMapper;
    private final PostService postService;
    private final UserFacade userFacade;
    private final ObjectProvider<GoodsQueryFacade> goodsFacadeProvider;

    public FeedService(PostMapper postMapper,
                       PostTopicRelMapper postTopicRelMapper,
                       PostService postService,
                       UserFacade userFacade,
                       ObjectProvider<GoodsQueryFacade> goodsFacadeProvider) {
        this.postMapper = postMapper;
        this.postTopicRelMapper = postTopicRelMapper;
        this.postService = postService;
        this.userFacade = userFacade;
        this.goodsFacadeProvider = goodsFacadeProvider;
    }

    /**
     * 动态页信息流（FR-M2-01/02 / GET /feed）。
     */
    public PageResult<FeedItem> feed(Long viewerId, String tab, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        String t = tab == null || tab.trim().isEmpty() ? TAB_RECOMMEND : tab.trim();

        switch (t) {
            case TAB_FOLLOW:
                // 契约缺口（《13》无关注接口）：按文档实现只读空态，不臆造过滤条件
                return PageResult.empty(p, s);
            case TAB_CAMPUS:
                return campusFeed(viewerId, p, s);
            case TAB_ACTIVITY:
                return activityFeed(viewerId, p, s);
            case TAB_RECOMMEND:
            default:
                return recommendFeed(viewerId, p, s);
        }
    }

    /**
     * 首页推荐流（FR-M2-08/09 / GET /home/feeds）。
     *
     * <p>结构：机会推荐（在售商品）+ 信息推荐（公开动态）混排。
     * 商品部分由交易模块 {@link GoodsQueryFacade} 提供，未装配时仅返回动态。
     */
    public Map<String, Object> homeFeeds(Long viewerId, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        Map<String, Object> data = new LinkedHashMap<>(6);

        List<FeedItem> items = new ArrayList<>();

        // 1) 机会推荐：在售商品（交易模块提供）
        GoodsQueryFacade goodsFacade = goodsFacadeProvider.getIfAvailable();
        if (goodsFacade != null) {
            items.addAll(goodsFacade.onSaleForFeed(p, Math.max(1, s / 2)));
        }

        // 2) 信息推荐：公开动态
        PageResult<FeedItem> postPage = recommendFeed(viewerId, p, Math.max(1, s - items.size()));
        items.addAll(postPage.getList());

        data.put("list", items);
        data.put("total", postPage.getTotal()
                + (goodsFacade == null ? 0 : goodsFacade.onSaleCount()));
        data.put("page", p);
        data.put("size", s);
        return data;
    }

    /* ==================== 各页签实现 ==================== */

    private PageResult<FeedItem> recommendFeed(Long viewerId, int page, int size) {
        Page<Post> p = new Page<>(page, size);
        postMapper.selectPage(p, postService.visibleWrapper(viewerId)
                .orderByDesc(Post::getIsTop)
                .orderByDesc(Post::getCreatedAt));
        return PageResult.of(postService.toFeedItems(viewerId, p.getRecords()),
                p.getTotal(), page, size);
    }

    /** 校园页签：命中校园话题（topic.type = 7）的公开动态 */
    private PageResult<FeedItem> campusFeed(Long viewerId, int page, int size) {
        List<PostTopicRel> rels = postTopicRelMapper.selectList(
                new LambdaQueryWrapper<PostTopicRel>().select(PostTopicRel::getPostId));
        Set<Long> postIds = new HashSet<>();
        for (PostTopicRel r : rels) {
            postIds.add(r.getPostId());
        }
        if (postIds.isEmpty()) {
            return PageResult.empty(page, size);
        }
        Page<Post> p = new Page<>(page, size);
        postMapper.selectPage(p, postService.visibleWrapper(viewerId)
                .in(Post::getId, postIds)
                .orderByDesc(Post::getCreatedAt));
        return PageResult.of(postService.toFeedItems(viewerId, p.getRecords()),
                p.getTotal(), page, size);
    }

    /**
     * 活动页签：组队需求类动态（post_type = 4 组队需求）。
     *
     * <p>说明：《13》未定义 /feed 页签的过滤字段，此处按《10》FR-M2-02
     * 「活动页签展示活动/竞赛相关信息」的语义，取 post 表中关联活动/竞赛的内容。
     */
    private PageResult<FeedItem> activityFeed(Long viewerId, int page, int size) {
        Page<Post> p = new Page<>(page, size);
        postMapper.selectPage(p, postService.visibleWrapper(viewerId)
                .and(w -> w.isNotNull(Post::getLinkType)
                        .in(Post::getLinkType, Enums.TargetType.ACTIVITY, Enums.TargetType.COMPETITION)
                        .or().eq(Post::getPostType, Post.TYPE_TEAM_NEED))
                .orderByDesc(Post::getCreatedAt));
        return PageResult.of(postService.toFeedItems(viewerId, p.getRecords()),
                p.getTotal(), page, size);
    }

    /* ==================== 搜索（FR-M2-07） ==================== */

    /**
     * 全局搜索（《13》§3.2 GET /search?kw=&type=）。
     *
     * @param type '' / post / topic / notice / message
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

        // 动态检索（FULLTEXT ngram；退化为 LIKE 亦可用）
        if (t.isEmpty() || "post".equals(t)) {
            Page<Post> pageObj = new Page<>(p, s);
            postMapper.selectPage(pageObj, postService.visibleWrapper(viewerId)
                    .like(Post::getContent, keyword)
                    .orderByDesc(Post::getCreatedAt));
            List<Map<String, Object>> list = new ArrayList<>();
            for (FeedItem item : postService.toFeedItems(viewerId, pageObj.getRecords())) {
                Map<String, Object> m = new LinkedHashMap<>(6);
                m.put("contentType", FeedItem.TYPE_POST);
                m.put("contentId", item.getContentId());
                m.put("text", item.getText());
                m.put("images", item.getImages());
                m.put("user", item.getUser());
                m.put("createdAt", item.getCreatedAt());
                list.add(m);
            }
            return PageResult.of(list, pageObj.getTotal(), p, s);
        }
        // 其它类型（topic / notice / message）由各自模块接口承载
        return PageResult.empty(p, s);
    }

    /** 搜索历史（GET /search/history）——暂为空实现，Redis 接入后补 */
    public List<Map<String, Object>> searchHistory(Long userId) {
        return Collections.emptyList();
    }
}
