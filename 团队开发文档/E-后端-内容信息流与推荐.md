# E · 后端开发文档 —— 内容信息流与推荐

> **成员**：E ｜ **基线**：《14》T0.3/T1.6（后端半）/T1.7/T1.8（后端半）/T2.2/T2.3（后端半）/T2.8（后端半） ｜ 《19》§1.3
> **上游**：《12》§2.2 表 ｜ 《13》M2 接口 ｜ 《11》§3.2 信息流聚合

---

## 1. 负责范围

| WBS | 功能 | FR | 核心接口 |
|---|---|---|---|
| T1.6 | 动态 CRUD + 机审接入（D 的 DFA） | M2-04 | `POST/PUT/DELETE /posts` |
| T1.6 | 信息流四页签聚合 | M2-01/02 | `GET /feed?tab=`、`GET /home/feeds` |
| T1.7 | 评论/点赞/收藏 + Redis 计数回写 | M2-03 | posts 互动系列 |
| T1.8 | 全局搜索 + 历史 + 热搜 | M2-07 | `/search` 系列 |
| T2.2 | 推荐画像与排序公式 | M2-08 | `/home/feeds`（推荐参数化） |
| T2.3 | 收藏联动、浏览历史、求购反向推送匹配 | M4-06 | favorite/history、推送事件 |
| T2.8 | 屏蔽双向过滤 + 不感兴趣负反馈 | M2-11 | block/dislike |

## 2. 关键设计

### 2.1 信息流聚合（《11》§3.2，ADR-2 拉模式）

```
写入时：各域发布成功 → Spring 事件 → FeedIndexService 写 Redis ZSet
         key: feed:{tab}  member: {contentType}:{contentId}  score: 时间戳+权重
读取时：ZREVRANGE 候选(5min 缓存) → 批量回表 → 过滤
         [status=1, visible_scope, 屏蔽黑名单, 未通过审核] → 组装卡片
排序：score = 专业匹配×0.4 + 热度×0.4 + 新鲜度×0.2
```

- 关注页：follower 集合 join 候选；校园页：全类型混排；
- **过滤顺序**（成本升序）：状态 → 可见范围 → 屏蔽 → 权限；
- 回表批量 ≤ 50 条/页，N+1 禁止（code review 检查项）。

### 2.2 推荐画像

- 冷启动：major 标签（user.major）匹配话题/内容标签；≥10 次浏览（browse_history 计数）切行为驱动；
- 行为权重：浏览 1 / 收藏 3 / 点赞 2 / 评论 4 / 负反馈 -5；
- 个人画像缓存 Redis（user:{id}:profile），变化事件重建；
- 不感兴趣：扣同类标签分 + 候选池剔除（《15》TC 联动）。

### 2.3 搜索

MySQL FULLTEXT ngram（post.content / goods.title）起步；`SearchService` 接口隔离，预留 ES 实现（ADR-5）；热搜 = Redis ZSet（搜索词计数，日清零保留 7 天榜）；历史服务端 + 本地双写。

### 2.4 求购反向推送（与 G 协作）

`goods_wanted` 创建 → 关键词分词 → 匹配 `goods`（status=1）发布者 → 投递 M3 通知事件（biz_type=10）。匹配逻辑在本模块（SearchService 复用），G 只发事件。

## 3. 接口依赖与被依赖

- 依赖：D 的 DFA/文件审核（发布前置）、M1 权限、M3 通知事件（收藏截止联动、反向推送）；
- 被依赖：A/B 的首页与动态页全部读接口；卡片数据结构是全前端契约，变更走 Apifox 评审（含 A）。

## 4. 里程碑排期

| 周 | 交付 |
|---|---|
| W1 | Apifox M2 域契约基线（T0.3） |
| W2–W4 | 动态 CRUD + Feed 聚合四页签 + 六类卡片数据 |
| W5 | 评论/点赞/收藏 + 计数回写 |
| W6 | 搜索三件套；M1 联调周支持 |
| W8–W9 | 推荐画像 + 权重公式 + 负反馈 |
| W10–W11 | 收藏联动 + 浏览历史 + 反向推送（β 验收：推荐点击率埋点上线） |
| W12–W15 | 性能调优（PT-01/03 压测）、屏蔽过滤完善 |

## 5. 自测 DoD

- [ ] 四页签数据正确性与排序可解释（专业权重样例可复现）；
- [ ] 分页 10 页无重复/丢失（TC-M2-07）；P95 ≤ 800ms（NFR-02）；
- [ ] 计数一致性：并发点赞/取消后 DB 与 Redis 漂移 = 0（TC-M2-12）；
- [ ] 私密动态他人不可见、被屏蔽用户双向不可见（TC-M2-09/11）；
- [ ] 搜索注入 payload 无害化（ST-03）。

## 6. 协作约定

- 与 G：反向推送事件契约（goods_wanted → M3）双方各自落单验证；商品卡字段契约共审；
- 与 F：通知事件结构 `NotifyEvent{userId,bizType,title,content,linkType,linkId,dedupKey}` 为跨模块标准，F 是消费方 owner；
- 与 A/B：feed 卡片结构变更须三方可视化评审（Apifox diff 页）。
