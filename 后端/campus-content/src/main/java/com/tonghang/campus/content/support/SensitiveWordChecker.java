package com.tonghang.campus.content.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.content.entity.SensitiveWord;
import com.tonghang.campus.content.mapper.SensitiveWordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词机审（FR-M2-04 / FR-M7-01）。
 *
 * <p>来源：
 * <ul>
 *   <li>《10-需求文档》FR-M2-04「发布动态（走敏感词机审）」；</li>
 *   <li>《12》§2.2 sensitive_word（level：1 拦截 / 2 复审）。</li>
 * </ul>
 *
 * <p>实现策略：启动后懒加载词库并缓存（TTL 5 分钟），命中拦截级 → 拒绝发布（40008）；
 * 命中复审级 → 落库 status=0 待审。词库为空时不拦截，保证 P0 链路可跑通。
 */
@Component
public class SensitiveWordChecker {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordChecker.class);

    /** 词库缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final SensitiveWordMapper mapper;

    private volatile Set<String> blockWords = Collections.emptySet();
    private volatile Set<String> reviewWords = Collections.emptySet();
    private volatile long loadedAt = 0L;

    public SensitiveWordChecker(SensitiveWordMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 扫描文本。
     *
     * @param text 待检文本
     * @return 扫描结果；词库为空时返回 {@link Result#pass()}
     */
    public Result scan(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Result.pass();
        }
        ensureLoaded();
        String lower = text.toLowerCase();

        for (String w : blockWords) {
            if (!w.isEmpty() && lower.contains(w)) {
                return Result.block(w);
            }
        }
        for (String w : reviewWords) {
            if (!w.isEmpty() && lower.contains(w)) {
                return Result.review(w);
            }
        }
        return Result.pass();
    }

    /** 手动刷新词库（运营侧词库变更后可调用） */
    public synchronized void refresh() {
        try {
            List<SensitiveWord> all = mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                    .eq(SensitiveWord::getStatus, 1));
            Set<String> blocks = new HashSet<>();
            Set<String> reviews = new HashSet<>();
            for (SensitiveWord w : all) {
                if (w.getWord() == null || w.getWord().trim().isEmpty()) {
                    continue;
                }
                String word = w.getWord().trim().toLowerCase();
                if (w.getLevel() != null && w.getLevel() == SensitiveWord.LEVEL_BLOCK) {
                    blocks.add(word);
                } else {
                    reviews.add(word);
                }
            }
            this.blockWords = Collections.unmodifiableSet(blocks);
            this.reviewWords = Collections.unmodifiableSet(reviews);
            this.loadedAt = System.currentTimeMillis();
            log.info("[M2] 敏感词库已加载：拦截 {} 条 / 复审 {} 条", blocks.size(), reviews.size());
        } catch (Exception e) {
            // 词库不可用时降级为「不拦截」，不阻断用户发布
            log.warn("[M2] 敏感词库加载失败，本轮降级为不拦截：{}", e.getMessage());
            this.loadedAt = System.currentTimeMillis();
        }
    }

    private void ensureLoaded() {
        if (System.currentTimeMillis() - loadedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (System.currentTimeMillis() - loadedAt > CACHE_TTL_MS) {
                    refresh();
                }
            }
        }
    }

    /**
     * 扫描结果。
     */
    public static class Result {
        /** 是否命中拦截级 */
        private final boolean blocked;
        /** 是否需人工复审 */
        private final boolean needReview;
        private final String hitWord;

        private Result(boolean blocked, boolean needReview, String hitWord) {
            this.blocked = blocked;
            this.needReview = needReview;
            this.hitWord = hitWord;
        }

        static Result pass() {
            return new Result(false, false, null);
        }

        static Result block(String word) {
            return new Result(true, false, word);
        }

        static Result review(String word) {
            return new Result(false, true, word);
        }

        public boolean isBlocked() {
            return blocked;
        }

        public boolean isNeedReview() {
            return needReview;
        }

        public String getHitWord() {
            return hitWord;
        }
    }
}
