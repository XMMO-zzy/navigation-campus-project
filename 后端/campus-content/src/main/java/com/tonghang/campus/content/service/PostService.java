package com.tonghang.campus.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.redis.RedisKeys;
import com.tonghang.campus.common.redis.RedisSupport;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.dto.FeedItem;
import com.tonghang.campus.content.entity.BrowseHistory;
import com.tonghang.campus.content.entity.Comment;
import com.tonghang.campus.content.entity.Favorite;
import com.tonghang.campus.content.entity.LikeRecord;
import com.tonghang.campus.content.entity.Post;
import com.tonghang.campus.content.entity.PostTopicRel;
import com.tonghang.campus.content.entity.Topic;
import com.tonghang.campus.content.mapper.BrowseHistoryMapper;
import com.tonghang.campus.content.mapper.CommentMapper;
import com.tonghang.campus.content.mapper.FavoriteMapper;
import com.tonghang.campus.content.mapper.LikeRecordMapper;
import com.tonghang.campus.content.mapper.PostMapper;
import com.tonghang.campus.content.mapper.PostTopicRelMapper;
import com.tonghang.campus.content.mapper.TopicMapper;
import com.tonghang.campus.content.support.JsonUtil;
import com.tonghang.campus.content.support.SensitiveWordChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态（内容）服务（M2）。
 *
 * <p>来源：《13-API接口文档》§3.2：
 * <ul>
 *   <li>POST /posts 发布动态（走敏感词机审）— FR-M2-04</li>
 *   <li>GET /posts/{id} 动态详情 — FR-M2-02</li>
 *   <li>PUT/DELETE /posts/{id} 编辑/删除（仅本人，删为软删）— FR-M7-07</li>
 *   <li>POST|DELETE /posts/{id}/like 点赞/取消 — FR-M2-03</li>
 *   <li>POST|DELETE /posts/{id}/favorite 收藏/取消 — FR-M2-03</li>
 *   <li>GET|POST /posts/{id}/comments 评论列表/发表评论（校验评论权限）— FR-M2-03</li>
 *   <li>PUT /posts/{id}/dislike 不感兴趣 — FR-M2-11</li>
 * </ul>
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeRecordMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final TopicMapper topicMapper;
    private final PostTopicRelMapper postTopicRelMapper;
    private final UserFacade userFacade;
    private final SensitiveWordChecker sensitiveWordChecker;
    private final RedisSupport redis;
    private final BrowseHistoryMapper browseHistoryMapper;

    public PostService(PostMapper postMapper,
                       CommentMapper commentMapper,
                       LikeRecordMapper likeMapper,
                       FavoriteMapper favoriteMapper,
                       TopicMapper topicMapper,
                       PostTopicRelMapper postTopicRelMapper,
                       UserFacade userFacade,
                       SensitiveWordChecker sensitiveWordChecker,
                       RedisSupport redis,
                       BrowseHistoryMapper browseHistoryMapper) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.topicMapper = topicMapper;
        this.postTopicRelMapper = postTopicRelMapper;
        this.userFacade = userFacade;
        this.sensitiveWordChecker = sensitiveWordChecker;
        this.redis = redis;
        this.browseHistoryMapper = browseHistoryMapper;
    }

    /* ==================== 发布 / 编辑 / 删除 ==================== */

    /**
     * 发布动态（FR-M2-04 / POST /posts）。
     *
     * <p>机审：命中拦截级敏感词 → 40008；命中复审级 → status=0 待审，否则直接通过。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String content = str(body.get("content"));
        List<String> images = strList(body.get("images"));
        if ((content == null || content.isEmpty()) && images.isEmpty()) {
            throw BizException.param("请填写正文或上传图片");
        }
        if (content != null && content.length() > 1000) {
            throw BizException.param("正文不能超过 1000 个字");
        }

        // 机审：正文 + 话题名
        List<String> topics = strList(body.get("topics"));
        String scanText = (content == null ? "" : content) + " " + String.join(" ", topics);
        SensitiveWordChecker.Result scan = sensitiveWordChecker.scan(scanText);
        if (scan.isBlocked()) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.SENSITIVE_WORD,
                    "内容含违规词「" + scan.getHitWord() + "」，请修改后重试");
        }

        Post post = new Post();
        post.setUserId(userId);
        post.setPostType(intOf(body.get("postType"), Post.TYPE_IMAGE_TEXT));
        post.setContent(content);
        post.setImages(JsonUtil.toJson(images));
        post.setLinkType(intOrNull(body.get("linkType")));
        post.setLinkId(longOrNull(body.get("linkId")));
        post.setVisibleScope(scopeOf(body.get("visibleScope"), Enums.VisibleScope.PUBLIC));
        post.setCommentPermission(scopeOf(body.get("commentPermission"), Enums.CommentPermission.ALL));
        post.setLikeCnt(0);
        post.setCommentCnt(0);
        post.setFavoriteCnt(0);
        post.setViewCnt(0);
        post.setStatus(scan.isNeedReview() ? Post.STATUS_PENDING : Post.STATUS_PASSED);
        post.setIsTop(0);
        postMapper.insert(post);

        bindTopics(post.getId(), topics);

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("postId", post.getId());
        data.put("status", post.getStatus());
        data.put("statusText", post.getStatus() == Post.STATUS_PENDING ? "审核中" : "已发布");
        if (scan.isNeedReview()) {
            data.put("auditHint", "内容含「" + scan.getHitWord() + "」，已提交人工复审");
        }
        return data;
    }

    /** 动态详情（FR-M2-02 / GET /posts/{id}） */
    public FeedItem detail(Long viewerId, Long id) {
        Post post = requirePost(id);
        checkVisible(viewerId, post);
        incrementView(post);
        // 登录用户的浏览行为：① Redis 计数（FR-M2-08 画像就绪判定）② browse_history 落库（推荐画像数据源）
        if (viewerId != null) {
            redis.run(t -> t.opsForValue().increment(RedisKeys.browseCounter(viewerId)));
            recordBrowse(viewerId, id);
        }
        FeedItem item = new FeedItem();
        item.setContentType(FeedItem.TYPE_POST);
        item.setContentId(post.getId());
        item.setTitle(null);
        item.setUser(userMap(post.getUserId()));
        item.setText(post.getContent());
        item.setImages(resolveImages(post.getImages()));
        item.setTopics(topicNamesOf(post.getId()));
        Map<String, Object> cnt = new LinkedHashMap<>(4);
        cnt.put("like", nvl(post.getLikeCnt()));
        cnt.put("comment", nvl(post.getCommentCnt()));
        cnt.put("favorite", nvl(post.getFavoriteCnt()));
        cnt.put("view", nvl(post.getViewCnt()));
        item.setCnt(cnt);
        item.setLink(linkOf(post));
        item.setInteract(interactOf(viewerId, post.getId()));
        item.setCreatedAt(post.getCreatedAt());
        return item;
    }

    /**
     * 编辑动态（FR-M7-07 / PUT /posts/{id}，仅本人）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long userId, Long id, Map<String, Object> body) {
        Post post = requirePost(id);
        if (!post.getUserId().equals(userId)) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.FORBIDDEN, "只能编辑自己的动态");
        }
        if (body != null) {
            if (body.get("content") != null) {
                String content = String.valueOf(body.get("content"));
                if (content.length() > 1000) {
                    throw BizException.param("正文不能超过 1000 个字");
                }
                SensitiveWordChecker.Result scan = sensitiveWordChecker.scan(content);
                if (scan.isBlocked()) {
                    throw new BizException(com.tonghang.campus.common.api.ErrorCode.SENSITIVE_WORD,
                            "内容含违规词「" + scan.getHitWord() + "」，请修改后重试");
                }
                post.setContent(content);
                if (scan.isNeedReview()) {
                    post.setStatus(Post.STATUS_PENDING);
                }
            }
            if (body.get("images") != null) {
                post.setImages(JsonUtil.toJson(body.get("images")));
            }
            if (body.get("visibleScope") != null) {
                post.setVisibleScope(scopeOf(body.get("visibleScope"), post.getVisibleScope()));
            }
            if (body.get("commentPermission") != null) {
                post.setCommentPermission(scopeOf(body.get("commentPermission"), post.getCommentPermission()));
            }
        }
        postMapper.updateById(post);
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("postId", post.getId());
        data.put("status", post.getStatus());
        return data;
    }

    /** 删除动态（FR-M7-07，软删，仅本人） */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        Post post = requirePost(id);
        if (!post.getUserId().equals(userId)) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.FORBIDDEN, "只能删除自己的动态");
        }
        postMapper.deleteById(id);
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, id));
    }

    /* ==================== 点赞 / 收藏 ==================== */

    /** 点赞（FR-M2-03 / POST /posts/{id}/like） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> like(Long userId, Long id) {
        requirePost(id);
        Long cnt = likeMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, Enums.TargetType.POST)
                .eq(LikeRecord::getTargetId, id));
        if (cnt != null && cnt > 0) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.DUPLICATE, "已经点过赞了");
        }
        LikeRecord lr = new LikeRecord();
        lr.setUserId(userId);
        lr.setTargetType(Enums.TargetType.POST);
        lr.setTargetId(id);
        likeMapper.insert(lr);
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .setSql("like_cnt = like_cnt + 1"));
        return likeState(userId, id);
    }

    /** 取消点赞（FR-M2-03 / DELETE /posts/{id}/like） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unlike(Long userId, Long id) {
        requirePost(id);
        int deleted = likeMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, Enums.TargetType.POST)
                .eq(LikeRecord::getTargetId, id));
        if (deleted > 0) {
            postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, id)
                    .gt(Post::getLikeCnt, 0)
                    .setSql("like_cnt = like_cnt - 1"));
        }
        return likeState(userId, id);
    }

    /** 收藏（FR-M2-03 / POST /posts/{id}/favorite） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> favorite(Long userId, Long id) {
        requirePost(id);
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, Enums.TargetType.POST)
                .eq(Favorite::getTargetId, id));
        if (cnt != null && cnt > 0) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.DUPLICATE, "已经收藏过了");
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setTargetType(Enums.TargetType.POST);
        fav.setTargetId(id);
        favoriteMapper.insert(fav);
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .setSql("favorite_cnt = favorite_cnt + 1"));
        return favoriteState(userId, id);
    }

    /** 取消收藏（FR-M2-03 / DELETE /posts/{id}/favorite） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unfavorite(Long userId, Long id) {
        requirePost(id);
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, Enums.TargetType.POST)
                .eq(Favorite::getTargetId, id));
        if (deleted > 0) {
            postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, id)
                    .gt(Post::getFavoriteCnt, 0)
                    .setSql("favorite_cnt = favorite_cnt - 1"));
        }
        return favoriteState(userId, id);
    }

    /**
     * 不感兴趣（FR-M2-11 / PUT /posts/{id}/dislike）。
     *
     * <p>负反馈入画像；画像服务（Redis 行为流）未接入前仅记录浏览负向标记。
     */
    /**
     * 不感兴趣（负反馈）— FR-M2-11 / PUT /posts/{id}/dislike。
     *
     * <p>落库方式：Redis Set（《12》无对应表，已确认不改表结构）。
     * 记入后该动态会从本人所有信息流中消失，并在推荐排序中对同作者内容降权。
     * Redis 不可用时该操作静默降级，响应中的 {@code recorded} 会返回 false 供前端/排查识别。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> dislike(Long userId, Long id) {
        requirePost(id);
        redis.run(t -> t.opsForSet().add(RedisKeys.dislikedPosts(userId), String.valueOf(id)));
        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("postId", id);
        data.put("disliked", true);
        data.put("recorded", redis.isAvailable());
        return data;
    }

    /* ==================== 评论 ==================== */

    /** 评论列表（FR-M2-03 / GET /posts/{id}/comments） */
    public PageResult<Map<String, Object>> comments(Long viewerId, Long postId, Integer page, Integer size) {
        Post post = requirePost(postId);
        if (post.getCommentPermission() != null
                && post.getCommentPermission() == Enums.CommentPermission.CLOSED) {
            return PageResult.empty(PageUtil.normalizePage(page), PageUtil.normalizeSize(size));
        }
        Page<Comment> p = new Page<>(PageUtil.normalizePage(page), PageUtil.normalizeSize(size));
        LambdaQueryWrapper<Comment> cw = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getStatus, Comment.STATUS_NORMAL);
        // 屏蔽双向不可见（FR-M2-11）：被拉黑者的评论不出现在列表里
        if (viewerId != null) {
            Set<Long> blocked = userFacade.blockedEitherWay(viewerId);
            if (blocked != null && !blocked.isEmpty()) {
                cw.notIn(Comment::getUserId, blocked);
            }
        }
        cw.orderByDesc(Comment::getId);
        commentMapper.selectPage(p, cw);

        List<Map<String, Object>> list = new ArrayList<>(p.getRecords().size());
        for (Comment c : p.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("id", c.getId());
            item.put("user", userMap(c.getUserId()));
            item.put("content", c.getContent());
            item.put("parentId", c.getParentId());
            item.put("replyToUserId", c.getReplyToUserId());
            item.put("likeCnt", nvl(c.getLikeCnt()));
            item.put("createdAt", c.getCreatedAt());
            item.put("liked", viewerId != null && hasLiked(viewerId, c.getId(), Enums.TargetType.COMMENT));
            list.add(item);
        }
        return PageResult.of(list, p.getTotal(), PageUtil.normalizePage(page), PageUtil.normalizeSize(size));
    }

    /**
     * 发表评论（FR-M2-03 / POST /posts/{id}/comments，校验评论权限）。
     *
     * <p>权限口径（《12》§2.2 post.comment_permission，0 基）：
     * 0 所有人 / 1 关注的人 / 2 仅自己 / 3 关闭。
     * 契约缺口说明：《13》未定义关注接口，故「1 关注的人」在契约评审前
     * 仅允许作者本人评论（保守策略，避免越权放行）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> comment(Long userId, Long postId, Map<String, Object> body) {
        Post post = requirePost(postId);
        if (body == null || str(body.get("content")) == null) {
            throw BizException.param("评论内容不能为空");
        }
        String content = str(body.get("content"));
        if (content.length() > 500) {
            throw BizException.param("评论不能超过 500 个字");
        }

        int perm = post.getCommentPermission() == null ? Enums.CommentPermission.ALL : post.getCommentPermission();
        boolean isAuthor = post.getUserId().equals(userId);
        if (perm == Enums.CommentPermission.CLOSED) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.STATUS_CONFLICT, "作者已关闭评论");
        }
        if (perm == Enums.CommentPermission.SELF_ONLY && !isAuthor) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.FORBIDDEN, "仅作者本人可评论");
        }
        if (perm == Enums.CommentPermission.FOLLOWERS && !isAuthor) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.FORBIDDEN,
                    "仅作者关注的人可评论（关注关系接口待契约评审）");
        }

        SensitiveWordChecker.Result scan = sensitiveWordChecker.scan(content);
        if (scan.isBlocked()) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.SENSITIVE_WORD,
                    "评论含违规词「" + scan.getHitWord() + "」，请修改后重试");
        }

        Comment c = new Comment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setParentId(longOrNull(body.get("parentId")));
        c.setReplyToUserId(longOrNull(body.get("replyToUserId")));
        c.setContent(content);
        c.setLikeCnt(0);
        c.setStatus(scan.isNeedReview() ? Comment.STATUS_PENDING : Comment.STATUS_NORMAL);
        commentMapper.insert(c);

        if (c.getStatus() == Comment.STATUS_NORMAL) {
            postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("comment_cnt = comment_cnt + 1"));
        }

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("commentId", c.getId());
        data.put("status", c.getStatus());
        data.put("statusText", c.getStatus() == Comment.STATUS_PENDING ? "审核中" : "已发布");
        return data;
    }

    /* ==================== 供 FeedService 复用 ==================== */

    /** 批量把 Post 转为 FeedItem（自动带用户信息与互动态） */
    public List<FeedItem> toFeedItems(Long viewerId, List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new HashSet<>();
        Set<Long> postIds = new HashSet<>();
        for (Post p : posts) {
            userIds.add(p.getUserId());
            postIds.add(p.getId());
        }
        Map<Long, UserFacade.UserBrief> users = userFacade.getBatch(userIds);
        Map<Long, List<String>> topicMap = topicNamesOfBatch(postIds);

        // 当前用户已点赞/已收藏集合（避免逐条查询）
        Set<Long> likedSet = viewerId == null ? Collections.emptySet()
                : likedSet(viewerId, new ArrayList<>(postIds));
        Set<Long> favSet = viewerId == null ? Collections.emptySet()
                : favoritedSet(viewerId, new ArrayList<>(postIds));

        List<FeedItem> items = new ArrayList<>(posts.size());
        for (Post p : posts) {
            FeedItem item = new FeedItem();
            item.setContentType(FeedItem.TYPE_POST);
            item.setContentId(p.getId());
            item.setTitle(null);
            UserFacade.UserBrief u = users.get(p.getUserId());
            item.setUser(userMapOf(u, p.getUserId()));
            item.setText(p.getContent());
            item.setImages(resolveImages(p.getImages()));
            item.setTopics(topicMap.getOrDefault(p.getId(), Collections.emptyList()));
            Map<String, Object> cnt = new LinkedHashMap<>(4);
            cnt.put("like", nvl(p.getLikeCnt()));
            cnt.put("comment", nvl(p.getCommentCnt()));
            cnt.put("favorite", nvl(p.getFavoriteCnt()));
            item.setCnt(cnt);
            item.setLink(linkOf(p));
            Map<String, Object> it = new LinkedHashMap<>(4);
            it.put("liked", likedSet.contains(p.getId()));
            it.put("favorited", favSet.contains(p.getId()));
            item.setInteract(it);
            item.setCreatedAt(p.getCreatedAt());
            items.add(item);
        }
        return items;
    }

    /* ==================== 内部工具 ==================== */

    public Post requirePost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw BizException.notFound("动态不存在或已删除");
        }
        return post;
    }

    /** 可见性校验（《12》§2.2 visible_scope：0 公开 / 1 仅关注 / 2 私密） */
    private void checkVisible(Long viewerId, Post post) {
        int scope = post.getVisibleScope() == null ? Enums.VisibleScope.PUBLIC : post.getVisibleScope();
        if (scope == Enums.VisibleScope.PUBLIC) {
            return;
        }
        boolean isAuthor = viewerId != null && post.getUserId().equals(viewerId);
        if (isAuthor) {
            return;
        }
        if (scope == Enums.VisibleScope.PRIVATE) {
            throw new BizException(com.tonghang.campus.common.api.ErrorCode.FORBIDDEN, "该动态仅作者本人可见");
        }
        // 仅关注可见：契约缺口期（《13》无关注接口）保守拒绝
        throw new BizException(com.tonghang.campus.common.api.ErrorCode.FORBIDDEN,
                "该动态仅作者关注的人可见（关注关系接口待契约评审）");
    }

    /** 可见范围过滤条件（信息流用）：公开 或 自己发布 */
    /**
     * 动态可见性统一条件（所有动态查询都必须套用）。
     *
     * <p>来源：《10》FR-M2-05（可见范围）、FR-M2-11（屏蔽双向不可见 + 不感兴趣）。
     * 过滤三层：
     * <ol>
     *   <li>审核通过（status=1）；</li>
     *   <li>可见范围：游客只看公开；登录用户看公开 + 自己的；</li>
     *   <li>屏蔽关系双向过滤 + 剔除自己标记过「不感兴趣」的内容。</li>
     * </ol>
     */
    public LambdaQueryWrapper<Post> visibleWrapper(Long viewerId) {
        LambdaQueryWrapper<Post> w = new LambdaQueryWrapper<>();
        w.eq(Post::getStatus, Post.STATUS_PASSED);
        if (viewerId == null) {
            w.eq(Post::getVisibleScope, Enums.VisibleScope.PUBLIC);
            return w;
        }
        w.and(x -> x.eq(Post::getVisibleScope, Enums.VisibleScope.PUBLIC)
                .or().eq(Post::getUserId, viewerId));

        // 屏蔽双向不可见（FR-M2-11）
        Set<Long> blocked = userFacade.blockedEitherWay(viewerId);
        if (blocked != null && !blocked.isEmpty()) {
            w.notIn(Post::getUserId, blocked);
        }
        // 已标记「不感兴趣」的动态不再出现
        Set<Long> disliked = dislikedPostIds(viewerId);
        if (!disliked.isEmpty()) {
            w.notIn(Post::getId, disliked);
        }
        return w;
    }

    /**
     * 我标记为「不感兴趣」的动态 id 集合。
     *
     * <p>存储：Redis Set（《12》无对应表；已确认不改表结构）。
     * Redis 不可用时返回空集合 → 该功能静默降级（不影响信息流可用性）。
     */
    public Set<Long> dislikedPostIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> raw = redis.call(
                t -> t.opsForSet().members(RedisKeys.dislikedPosts(userId)),
                Collections.<String>emptySet());
        Set<Long> ids = new HashSet<>(raw.size());
        for (String s : raw) {
            try {
                ids.add(Long.valueOf(s));
            } catch (NumberFormatException ignore) {
                // 脏数据跳过，不影响主流程
            }
        }
        return ids;
    }

    /**
     * 当前用户的累计浏览数（Redis）。
     *
     * <p>用于 FR-M2-08「≥10 次浏览后切换行为驱动」的画像就绪判定。
     * Redis 不可用时返回 0 → 推荐自动退化为冷启动策略（热门 + 新发），不影响可用性。
     */
    public long browseCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        String v = redis.call(t -> t.opsForValue().get(RedisKeys.browseCounter(userId)), null);
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 写入浏览历史（《12》§2.2 browse_history：「推荐画像数据源」）。
     *
     * <p>同一用户对同一内容只保留一条，重复浏览只刷新 viewed_at，
     * 避免历史表随浏览次数无限膨胀（《12》未设唯一键，此处由应用层保证）。
     * 该数据属画像辅助，写入失败不影响详情返回。
     */
    private void recordBrowse(Long userId, Long postId) {
        try {
            BrowseHistory exist = browseHistoryMapper.selectOne(new LambdaQueryWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getUserId, userId)
                    .eq(BrowseHistory::getTargetType, Enums.TargetType.POST)
                    .eq(BrowseHistory::getTargetId, postId)
                    .last("LIMIT 1"));
            LocalDateTime now = LocalDateTime.now();
            if (exist != null) {
                exist.setViewedAt(now);
                browseHistoryMapper.updateById(exist);
                return;
            }
            BrowseHistory h = new BrowseHistory();
            h.setUserId(userId);
            h.setTargetType(Enums.TargetType.POST);
            h.setTargetId(postId);
            h.setViewedAt(now);
            browseHistoryMapper.insert(h);
        } catch (Exception e) {
            log.debug("[M2] 写入浏览历史失败 uid={} postId={}：{}", userId, postId, e.getMessage());
        }
    }

    private void incrementView(Post post) {
        postMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                .eq(Post::getId, post.getId())
                .setSql("view_cnt = view_cnt + 1"));
    }

    private Map<String, Object> userMap(Long userId) {
        return userMapOf(userFacade.get(userId), userId);
    }

    private Map<String, Object> userMapOf(UserFacade.UserBrief brief, Long fallbackId) {
        Map<String, Object> m = new LinkedHashMap<>(6);
        if (brief == null) {
            m.put("id", fallbackId);
            m.put("nickname", "用户" + fallbackId);
            m.put("schoolTag", "通航学院");
            m.put("followed", false);
            return m;
        }
        m.put("id", brief.getId());
        m.put("nickname", brief.getNickname());
        m.put("avatar", brief.getAvatar());
        m.put("schoolTag", brief.getSchoolTag());
        m.put("userType", brief.getUserType());
        m.put("followed", brief.isFollowed());
        return m;
    }

    private Map<String, Object> linkOf(Post post) {
        if (post.getLinkType() == null || post.getLinkId() == null) {
            return null;
        }
        Map<String, Object> link = new LinkedHashMap<>(4);
        link.put("type", post.getLinkType());
        link.put("id", post.getLinkId());
        link.put("title", linkTitle(post.getLinkType(), post.getLinkId()));
        return link;
    }

    /**
     * 关联内容标题解析。
     *
     * <p>跨模块读取（活动/竞赛/搭子/商品）由各自 Facade 提供；
     * 骨架阶段返回类型名占位，接入后由对应模块补充标题与 deadlineLeft。
     */
    private String linkTitle(Integer linkType, Long linkId) {
        if (linkType == null) {
            return null;
        }
        switch (linkType) {
            case Enums.TargetType.ACTIVITY:
                return "相关活动";
            case Enums.TargetType.COMPETITION:
                return "相关竞赛";
            case Enums.TargetType.GOODS:
                return "相关商品";
            case Enums.TargetType.BUDDY:
                return "相关搭子";
            default:
                return null;
        }
    }

    private Map<String, Object> interactOf(Long viewerId, Long postId) {
        Map<String, Object> it = new LinkedHashMap<>(4);
        if (viewerId == null) {
            it.put("liked", false);
            it.put("favorited", false);
            return it;
        }
        it.put("liked", hasLiked(viewerId, postId, Enums.TargetType.POST));
        it.put("favorited", isFavorited(viewerId, postId));
        return it;
    }

    private Map<String, Object> likeState(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("postId", postId);
        data.put("liked", hasLiked(userId, postId, Enums.TargetType.POST));
        data.put("likeCnt", post == null ? 0 : nvl(post.getLikeCnt()));
        return data;
    }

    private Map<String, Object> favoriteState(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("postId", postId);
        data.put("favorited", isFavorited(userId, postId));
        data.put("favoriteCnt", post == null ? 0 : nvl(post.getFavoriteCnt()));
        return data;
    }

    public boolean hasLiked(Long userId, Long targetId, int targetType) {
        Long cnt = likeMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, targetType)
                .eq(LikeRecord::getTargetId, targetId));
        return cnt != null && cnt > 0;
    }

    public boolean isFavorited(Long userId, Long targetId) {
        Long cnt = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, Enums.TargetType.POST)
                .eq(Favorite::getTargetId, targetId));
        return cnt != null && cnt > 0;
    }

    private Set<Long> likedSet(Long userId, List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<LikeRecord> rows = likeMapper.selectList(new LambdaQueryWrapper<LikeRecord>()
                .eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, Enums.TargetType.POST)
                .in(LikeRecord::getTargetId, postIds));
        Set<Long> set = new HashSet<>(rows.size());
        for (LikeRecord r : rows) {
            set.add(r.getTargetId());
        }
        return set;
    }

    private Set<Long> favoritedSet(Long userId, List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Favorite> rows = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, Enums.TargetType.POST)
                .in(Favorite::getTargetId, postIds));
        Set<Long> set = new HashSet<>(rows.size());
        for (Favorite r : rows) {
            set.add(r.getTargetId());
        }
        return set;
    }

    /** 图片 id → URL（file_object 由 M7 提供；骨架阶段原样返回字符串 URL） */
    private List<String> resolveImages(String imagesJson) {
        return JsonUtil.toStringList(imagesJson);
    }

    private List<String> topicNamesOf(Long postId) {
        List<PostTopicRel> rels = postTopicRelMapper.selectList(new LambdaQueryWrapper<PostTopicRel>()
                .eq(PostTopicRel::getPostId, postId));
        return topicNames(rels);
    }

    private Map<Long, List<String>> topicNamesOfBatch(Set<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PostTopicRel> rels = postTopicRelMapper.selectList(new LambdaQueryWrapper<PostTopicRel>()
                .in(PostTopicRel::getPostId, postIds));
        Map<Long, List<String>> map = new HashMap<>(postIds.size() * 2);
        if (rels.isEmpty()) {
            return map;
        }
        Set<Long> topicIds = new HashSet<>();
        for (PostTopicRel r : rels) {
            topicIds.add(r.getTopicId());
        }
        Map<Long, String> names = new HashMap<>(topicIds.size() * 2);
        for (Topic t : topicMapper.selectBatchIds(topicIds)) {
            names.put(t.getId(), t.getName());
        }
        for (PostTopicRel r : rels) {
            String name = names.get(r.getTopicId());
            if (name != null) {
                map.computeIfAbsent(r.getPostId(), k -> new ArrayList<>(2)).add(name);
            }
        }
        return map;
    }

    private List<String> topicNames(List<PostTopicRel> rels) {
        if (rels == null || rels.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = new HashSet<>();
        for (PostTopicRel r : rels) {
            ids.add(r.getTopicId());
        }
        List<Topic> topics = topicMapper.selectBatchIds(ids);
        List<String> names = new ArrayList<>(topics.size());
        for (Topic t : topics) {
            names.add(t.getName());
        }
        return names;
    }

    /** 绑定话题（不存在则创建，use_count +1） */
    private void bindTopics(Long postId, List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return;
        }
        List<String> normalized = new ArrayList<>(topics.size());
        for (String t : topics) {
            if (t == null || t.trim().isEmpty()) {
                continue;
            }
            String name = t.trim().replace("#", "");
            if (name.length() > 32) {
                name = name.substring(0, 32);
            }
            if (!normalized.contains(name)) {
                normalized.add(name);
            }
        }
        for (String name : normalized) {
            Topic topic = topicMapper.selectOne(new LambdaQueryWrapper<Topic>()
                    .eq(Topic::getName, name).last("LIMIT 1"));
            if (topic == null) {
                topic = new Topic();
                topic.setName(name);
                topic.setType(7);
                topic.setUseCount(1);
                topicMapper.insert(topic);
            } else {
                topicMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Topic>()
                        .eq(Topic::getId, topic.getId())
                        .setSql("use_count = use_count + 1"));
            }
            PostTopicRel rel = new PostTopicRel();
            rel.setPostId(postId);
            rel.setTopicId(topic.getId());
            try {
                postTopicRelMapper.insert(rel);
            } catch (Exception ignore) {
                // uk_pt 唯一键冲突：同一动态同一话题，忽略
            }
        }
    }

    /* ---------------- 类型转换小工具 ---------------- */

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private List<String> strList(Object v) {
        if (v == null) {
            return Collections.emptyList();
        }
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) v) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        }
        return Collections.singletonList(String.valueOf(v));
    }

    private Integer intOf(Object v, int fallback) {
        Integer i = intOrNull(v);
        return i == null ? fallback : i;
    }

    private Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int scopeOf(Object v, int fallback) {
        Integer i = intOrNull(v);
        return i == null ? fallback : i;
    }
}
