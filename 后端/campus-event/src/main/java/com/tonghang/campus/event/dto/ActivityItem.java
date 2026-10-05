package com.tonghang.campus.event.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 活动列表条目（《13-API接口文档》§3.5 GET /activities）。
 *
 * <p>与《12-数据库设计文档》§2.5 activity 列一一对应；
 * 列表场景不返回 content/rule（详情接口才返回），减少报文体积。
 */
public class ActivityItem {

    /** 我是否已报名（游客 / 未报名为 false） */
    private boolean enrolled;

    private Long id;
    private String title;
    private String cover;
    /** 1 校园活动 2 学术讲座 3 学术调研 */
    private Integer category;
    private String organizer;
    private String place;
    private Integer capacity;
    private Integer enrolledCnt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime enrollStart;
    private LocalDateTime enrollDeadline;
    private Integer needReview;
    /** 0 待审 1 报名中 2 进行中 3 已结束 4 已取消 */
    private Integer status;
    private LocalDateTime createdAt;

    /** 转 Map 输出（Controller 直接返回 Map，避免额外序列化类） */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>(20);
        m.put("id", id);
        m.put("title", title);
        m.put("cover", cover);
        m.put("category", category);
        m.put("categoryText", categoryText(category));
        m.put("organizer", organizer);
        m.put("place", place);
        m.put("capacity", capacity);
        m.put("enrolledCnt", enrolledCnt == null ? 0 : enrolledCnt);
        m.put("startAt", startAt);
        m.put("endAt", endAt);
        m.put("enrollStart", enrollStart);
        m.put("enrollDeadline", enrollDeadline);
        m.put("needReview", needReview == null ? 0 : needReview);
        m.put("status", status);
        m.put("statusText", statusText(status));
        m.put("enrolled", enrolled);
        m.put("createdAt", createdAt);
        return m;
    }

    /** 分类文案（与 topic / sys_config 口径一致，见《13》§3.5） */
    public static String categoryText(Integer category) {
        if (category == null) {
            return null;
        }
        switch (category) {
            case 1:
                return "校园活动";
            case 2:
                return "学术讲座";
            case 3:
                return "学术调研";
            default:
                return "其他";
        }
    }

    /** 状态文案（《12》§2.5 activity.status 注释） */
    public static String statusText(Integer status) {
        if (status == null) {
            return "待审核";
        }
        switch (status) {
            case 0:
                return "待审核";
            case 1:
                return "报名中";
            case 2:
                return "进行中";
            case 3:
                return "已结束";
            case 4:
                return "已取消";
            default:
                return "待审核";
        }
    }

    /* ---------------- getter / setter ---------------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getEnrolledCnt() { return enrolledCnt; }
    public void setEnrolledCnt(Integer enrolledCnt) { this.enrolledCnt = enrolledCnt; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public LocalDateTime getEnrollStart() { return enrollStart; }
    public void setEnrollStart(LocalDateTime enrollStart) { this.enrollStart = enrollStart; }
    public LocalDateTime getEnrollDeadline() { return enrollDeadline; }
    public void setEnrollDeadline(LocalDateTime enrollDeadline) { this.enrollDeadline = enrollDeadline; }
    public Integer getNeedReview() { return needReview; }
    public void setNeedReview(Integer needReview) { this.needReview = needReview; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isEnrolled() { return enrolled; }
    public void setEnrolled(boolean enrolled) { this.enrolled = enrolled; }
}
