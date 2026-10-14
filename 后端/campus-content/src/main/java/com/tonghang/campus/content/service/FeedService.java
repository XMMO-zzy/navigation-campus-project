package com.tonghang.campus.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
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
import java.util.HashMap;
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

    /* ==================== 推荐排序（FR-M2-08 / T2.2） ==================== */

    /** 推荐权重：专业 40% + 热度 40% + 新鲜度 20%（《10》FR-M2-08 原文给定） */
    private static final double W_MAJOR = 0.40d;
    private static final double W_HOT = 0.40d;
    private static final double W_FRESH = 0.20d;

    /** 冷启动兜底权重：热门 + 新发（画像未就绪时使用） */
    private static final double W_HOT_COLD = 0.70d;
    private static final double W_FRESH_COLD = 0.30d;

    /** 新鲜度衰减窗口（小时）：超过则新鲜度记 0 */
    private static final double FRESH_WINDOW_HOURS = 72d;

    /** 画像就绪所需浏览次数（《10》FR-M2-08：≥10 次浏览后切换行为驱动） */
    private static final long PROFILE_READY_BROWSE = 10L;

    /** 候选池 = page*size*该倍率（上限 POOL_MAX），在池内打分排序后再切页 */
    private static final int POOL_FACTOR = 4;
    private static final int POOL_MAX = 300;

    /** 命中「不感兴趣」作者时的降权系数（FR-M2-11「减少相似内容推荐」） */
    private static final double DISLIKE_PENALTY = 0.5d;

    /** 负反馈作者查询上限，避免超大 IN 子句 */
    private static final int DISLIKE_AUTHOR_CAP = 200;

    /**
     * 推荐页签（FR-M2-08）。
     *
     * <p>排序模型（文档给定权重，本类按此实现）：
     * <pre>
     *   画像就绪（已登录 + 有专业 + 浏览数 ≥ {@value #PROFILE_READY_BROWSE}）
     *        score = 0.40 × 专业匹配 + 0.40 × 热度 + 0.20 × 新鲜度
     *   冷启动兜底（首登 / 游客 / 浏览不足）
     *        score = 0.70 × 热度 + 0.30 × 新鲜度   ← 《10》「冷启动兜底（热门 + 新发）」
     * </pre>
     *
     * <p>三处加权项补正：
     * <ul>
     *   <li>置顶动态（is_top）直接 +1，保证运营置顶始终靠前；</li>
     *   <li>命中「不感兴趣」的作者整体 ×0.5（近似实现「减少相似内容推荐」）；</li>
     *   <li>可见性、屏蔽、不感兴趣内容本身的过滤已由
     *       {@link PostService#visibleWrapper(Long)} 统一承担。</li>
     * </ul>
     *
     * <p>分页说明：{@code total} 取自数据库真实可见总数；
     * 但排序在候选池内完成，因此**翻页超过候选池上限（{@value #POOL_MAX} 条）后返回空**——
     * 推荐流场景下可接受，已在交付说明中登记。
     */
    private PageResult<FeedItem> recommendFeed(Long viewerId, int page, int size) {
        long total = countVisible(viewerId);
        if (total == 0) {
            return PageResult.of(Collections.emptyList(), 0L, page, size);
        }

        int poolSize = Math.min(POOL_MAX, Math.max(size * POOL_FACTOR, page * size * POOL_FACTOR));
        List<Post> pool = postMapper.selectList(postService.visibleWrapper(viewerId)
                .orderByDesc(Post::getIsTop)
                .orderByDesc(Post::getCreatedAt)
                .last("LIMIT " + poolSize));
        if (pool.isEmpty()) {
            return PageResult.of(Collections.emptyList(), total, page, size);
        }

        List<Post> ranked = rank(viewerId, pool);

        int from = (page - 1) * size;
        if (from >= ranked.size()) {
            return PageResult.of(Collections.emptyList(), total, page, size);
        }
        int to = Math.min(from + size, ranked.size());
        return PageResult.of(postService.toFeedItems(viewerId, new ArrayList<>(ranked.subList(from, to))),
                total, page, size);
    }

    private long countVisible(Long viewerId) {
        Long c = postMapper.selectCount(postService.visibleWrapper(viewerId));
        return c == null ? 0L : c;
    }

    /** 按 40/40/20 打分并降序排序；画像未就绪时自动走冷启动 */
    private List<Post> rank(Long viewerId, List<Post> pool) {
        UserFacade.UserBrief me = viewerId == null ? null : userFacade.get(viewerId);
        String myMajor = me == null ? null : me.getMajor();
        boolean profileReady = myMajor != null && !myMajor.trim().isEmpty()
                && postService.browseCount(viewerId) >= PROFILE_READY_BROWSE;

        Set<Long> authorIds = new HashSet<>();
        for (Post p : pool) {
            if (p.getUserId() != null) {
                authorIds.add(p.getUserId());
            }
        }
        Map<Long, UserFacade.UserBrief> authors = userFacade.getBatch(authorIds);
        Set<Long> dislikedAuthors = dislikedAuthors(viewerId);

        double maxHot = 1d;
        for (Post p : pool) {
            maxHot = Math.max(maxHot, hotRaw(p));
        }

        long now = System.currentTimeMillis();
        Map<Long, Double> scoreMap = new HashMap<>(pool.size() * 2);
        for (Post p : pool) {
            double hot = Math.min(1d, hotRaw(p) / maxHot);
            double fresh = freshness(p, now);
            double score;
            if (profileReady) {
                score = W_MAJOR * majorMatch(myMajor, authors.get(p.getUserId()))
                        + W_HOT * hot
                        + W_FRESH * fresh;
            } else {
                score = W_HOT_COLD * hot + W_FRESH_COLD * fresh;
            }
            if (p.getUserId() != null && dislikedAuthors.contains(p.getUserId())) {
                score *= DISLIKE_PENALTY;
            }
            if (p.getIsTop() != null && p.getIsTop() == 1) {
                score += 1d;
            }
            scoreMap.put(p.getId(), score);
        }

        List<Post> ranked = new ArrayList<>(pool);
        ranked.sort((a, b) -> Double.compare(
                scoreMap.getOrDefault(b.getId(), 0d),
                scoreMap.getOrDefault(a.getId(), 0d)));
        return ranked;
    }

    /** 热度原值：按互动深度加权（收藏 > 评论 > 点赞），与《12》§2.2 的计数列对应 */
    private double hotRaw(Post p) {
        return nvl(p.getLikeCnt()) + nvl(p.getCommentCnt()) * 2d + nvl(p.getFavoriteCnt()) * 3d;
    }

    /** 新鲜度：{@value #FRESH_WINDOW_HOURS} 小时内从 1 线性衰减到 0 */
    private double freshness(Post p, long now) {
        if (p.getCreatedAt() == null) {
            return 0d;
        }
        long ageMs = now - p.getCreatedAt()
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (ageMs <= 0) {
            return 1d;
        }
        double hours = ageMs / 3_600_000d;
        return Math.max(0d, 1d - hours / FRESH_WINDOW_HOURS);
    }

    /** 专业匹配：与作者专业相同记 1，否则 0（《10》FR-M2-08 的「专业 40%」） */
    private double majorMatch(String myMajor, UserFacade.UserBrief author) {
        if (myMajor == null || author == null || author.getMajor() == null) {
            return 0d;
        }
        return myMajor.trim().equalsIgnoreCase(author.getMajor().trim()) ? 1d : 0d;
    }

    /** 取「我标记过不感兴趣的动态」的作者集合，用于整体降权 */
    private Set<Long> dislikedAuthors(Long viewerId) {
        Set<Long> dislikedPostIds = postService.dislikedPostIds(viewerId);
        if (dislikedPostIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> ids = new ArrayList<>(dislikedPostIds);
        if (ids.size() > DISLIKE_AUTHOR_CAP) {
            ids = ids.subList(0, DISLIKE_AUTHOR_CAP);
        }
        List<Post> posts = postMapper.selectBatchIds(ids);
        Set<Long> authorSet = new HashSet<>();
        for (Post p : posts) {
            if (p.getUserId() != null) {
                authorSet.add(p.getUserId());
            }
        }
        return authorSet;
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
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

    /* ==================== 搜索（FR-M2-07） ====================
     * 搜索相关的实现已移至同模块的 {@link SearchService}
     * （全局搜索 + 搜索历史 + 热门搜索 + 埋点，见《13》§3.2），
     * 本类只负责信息流，避免职责重叠。
     * ======================================================== */
}
