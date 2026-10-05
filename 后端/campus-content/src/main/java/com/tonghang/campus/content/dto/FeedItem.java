package com.tonghang.campus.content.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 信息流条目（《13》§3.2 GET /feed 响应 list 元素）。
 *
 * <p>契约对齐示例（《13》§4 ◆ GET /feed）：
 * <pre>
 * { "contentType": 1, "contentId": 501, "title": null,
 *   "user": { "id": 10001, "nickname": "小航", "schoolTag": "XX学院", "followed": false },
 *   "text": "求组队打互联网+，缺后端…", "images": ["https://cos.../1.jpg"],
 *   "topics": ["竞赛", "组队"],
 *   "cnt": { "like": 12, "comment": 3, "favorite": 5 },
 *   "link": { "type": 4, "id": 301, "title": "互联网+大赛", "deadlineLeft": "3天" },
 *   "createdAt": "2026-11-20T14:00:00+08:00" }
 * </pre>
 *
 * <p>多态说明：《13》未逐字定义 contentType 值域，按 post/goods 两类内容聚合的语义
 * 取 1=动态、2=商品（与 §4 示例第一条 contentType=1 为动态一致）。
 */
public class FeedItem {

    /** 内容类型：动态 */
    public static final int TYPE_POST = 1;
    /** 内容类型：商品 */
    public static final int TYPE_GOODS = 2;

    private int contentType;
    private Long contentId;

    /** 商品标题；动态为 null（与 §4 示例一致） */
    private String title;

    /** 发布者（动态/商品均为 user 对象） */
    private Map<String, Object> user;

    /** 动态正文 */
    private String text;
    /** 动态图片 URL 列表 */
    private List<String> images;
    /** 动态话题 */
    private List<String> topics;

    /** 商品价格 */
    private java.math.BigDecimal price;
    /** 商品成色名称 */
    private String cond;
    /** 商品首图 */
    private String image;

    /** 计数：like/comment/favorite */
    private Map<String, Object> cnt;

    /** 关联内容（组队需求指向活动/竞赛等） */
    private Map<String, Object> link;

    /** 互动态（当前登录用户） */
    private Map<String, Object> interact;

    private LocalDateTime createdAt;

    /* ---------------- getter / setter ---------------- */

    public int getContentType() { return contentType; }
    public void setContentType(int contentType) { this.contentType = contentType; }
    public Long getContentId() { return contentId; }
    public void setContentId(Long contentId) { this.contentId = contentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Map<String, Object> getUser() { return user; }
    public void setUser(Map<String, Object> user) { this.user = user; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public List<String> getTopics() { return topics; }
    public void setTopics(List<String> topics) { this.topics = topics; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
    public String getCond() { return cond; }
    public void setCond(String cond) { this.cond = cond; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Map<String, Object> getCnt() { return cnt; }
    public void setCnt(Map<String, Object> cnt) { this.cnt = cnt; }
    public Map<String, Object> getLink() { return link; }
    public void setLink(Map<String, Object> link) { this.link = link; }
    public Map<String, Object> getInteract() { return interact; }
    public void setInteract(Map<String, Object> interact) { this.interact = interact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
