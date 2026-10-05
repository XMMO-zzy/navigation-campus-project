package com.tonghang.campus.event.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 竞赛列表条目（《13-API接口文档》§3.5 GET /competitions）。
 *
 * <p>与《12-数据库设计文档》§2.5 competition 列一一对应。
 */
public class CompetitionItem {

    /** 我是否已报名 */
    private boolean enrolled;

    private Long id;
    private String title;
    private String cover;
    /** 1 校级 2 省级 3 国家级 */
    private Integer level;
    private String organizer;
    private String place;
    private Integer capacity;
    private Integer enrolledCnt;
    private LocalDateTime enrollStart;
    private LocalDateTime enrollDeadline;
    private LocalDateTime preliminaryAt;
    private LocalDateTime finalAt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** 1 报名中 2 进行中 3 已结束 4 已取消 */
    private Integer status;
    private LocalDateTime createdAt;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>(20);
        m.put("id", id);
        m.put("title", title);
        m.put("cover", cover);
        m.put("level", level);
        m.put("levelText", levelText(level));
        m.put("organizer", organizer);
        m.put("place", place);
        m.put("capacity", capacity);
        m.put("enrolledCnt", enrolledCnt == null ? 0 : enrolledCnt);
        m.put("enrollStart", enrollStart);
        m.put("enrollDeadline", enrollDeadline);
        m.put("preliminaryAt", preliminaryAt);
        m.put("finalAt", finalAt);
        m.put("startAt", startAt);
        m.put("endAt", endAt);
        m.put("status", status);
        m.put("statusText", statusText(status));
        m.put("enrolled", enrolled);
        m.put("createdAt", createdAt);
        return m;
    }

    /** 级别文案（《12》§2.5 competition.level） */
    public static String levelText(Integer level) {
        if (level == null) {
            return null;
        }
        switch (level) {
            case 1:
                return "校级";
            case 2:
                return "省级";
            case 3:
                return "国家级";
            default:
                return "其他";
        }
    }

    /** 状态文案（《12》§2.5 competition.status） */
    public static String statusText(Integer status) {
        if (status == null) {
            return "报名中";
        }
        switch (status) {
            case 1:
                return "报名中";
            case 2:
                return "进行中";
            case 3:
                return "已结束";
            case 4:
                return "已取消";
            default:
                return "报名中";
        }
    }

    /* ---------------- getter / setter ---------------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getEnrolledCnt() { return enrolledCnt; }
    public void setEnrolledCnt(Integer enrolledCnt) { this.enrolledCnt = enrolledCnt; }
    public LocalDateTime getEnrollStart() { return enrollStart; }
    public void setEnrollStart(LocalDateTime enrollStart) { this.enrollStart = enrollStart; }
    public LocalDateTime getEnrollDeadline() { return enrollDeadline; }
    public void setEnrollDeadline(LocalDateTime enrollDeadline) { this.enrollDeadline = enrollDeadline; }
    public LocalDateTime getPreliminaryAt() { return preliminaryAt; }
    public void setPreliminaryAt(LocalDateTime preliminaryAt) { this.preliminaryAt = preliminaryAt; }
    public LocalDateTime getFinalAt() { return finalAt; }
    public void setFinalAt(LocalDateTime finalAt) { this.finalAt = finalAt; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isEnrolled() { return enrolled; }
    public void setEnrolled(boolean enrolled) { this.enrolled = enrolled; }
}
