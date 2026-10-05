package com.tonghang.campus.event.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搭子列表条目（《13-API接口文档》§3.6 GET /buddy-posts）。
 *
 * <p>与《12-数据库设计文档》§2.6 buddy_post 列对应。
 * <p>联系方式解锁规则（《10》FR-M6-04）：仅「我是发布者」或「我的申请已通过」时返回明文，
 * 否则返回 null 并在 contactUnlocked 标记 false。
 */
public class BuddyItem {

    /** 我是否已申请 */
    private boolean applied;
    /** 我的申请状态（未申请为 null） */
    private Integer applyStatus;
    /** 联系方式是否已解锁 */
    private boolean contactUnlocked;
    /** 我是否已收藏（契约缺口期固定 false，收藏表复用 content 的 favorite） */
    private boolean favorited;

    private Long id;
    private String title;
    /** 1 学习 2 运动 3 娱乐 4 旅行 5 饭搭子 6 技能 */
    private Integer buddyType;
    private List<String> tags;
    private String desc;
    private String place;
    private String timeDesc;
    private String requirement;
    private List<String> images;
    /** 仅解锁时返回 */
    private String contact;
    /** 0 不限 1 仅女性 */
    private Integer genderLimit;
    private Integer quota;
    /** 0 AA 1 请客 */
    private Integer feeMode;
    /** 0 寻找中 1 已找到 */
    private Integer matched;
    /** 0 待审 1 正常 2 已关闭 */
    private Integer status;
    private Integer applyCnt;
    private LocalDateTime createdAt;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>(24);
        m.put("id", id);
        m.put("title", title);
        m.put("buddyType", buddyType);
        m.put("buddyTypeText", typeText(buddyType));
        m.put("tags", tags);
        m.put("desc", desc);
        m.put("place", place);
        m.put("timeDesc", timeDesc);
        m.put("requirement", requirement);
        m.put("images", images);
        m.put("genderLimit", genderLimit == null ? 0 : genderLimit);
        m.put("quota", quota);
        m.put("feeMode", feeMode == null ? 0 : feeMode);
        m.put("feeModeText", feeText(feeMode));
        m.put("matched", matched == null ? 0 : matched);
        m.put("status", status);
        m.put("statusText", statusText(status));
        m.put("applyCnt", applyCnt == null ? 0 : applyCnt);
        m.put("applied", applied);
        m.put("applyStatus", applyStatus);
        m.put("contactUnlocked", contactUnlocked);
        m.put("contact", contactUnlocked ? contact : null);
        m.put("favorited", favorited);
        m.put("createdAt", createdAt);
        return m;
    }

    /** 搭子类型文案（《12》§2.6 buddy_post.buddy_type） */
    public static String typeText(Integer type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case 1:
                return "学习";
            case 2:
                return "运动";
            case 3:
                return "娱乐";
            case 4:
                return "旅行";
            case 5:
                return "饭搭子";
            case 6:
                return "技能";
            default:
                return "其他";
        }
    }

    /** 费用方式文案（《12》§2.6 buddy_post.fee_mode） */
    public static String feeText(Integer feeMode) {
        if (feeMode == null) {
            return "AA";
        }
        return feeMode == 1 ? "请客" : "AA";
    }

    /** 状态文案（《12》§2.6 buddy_post.status） */
    public static String statusText(Integer status) {
        if (status == null) {
            return "待审核";
        }
        switch (status) {
            case 0:
                return "待审核";
            case 1:
                return "寻找中";
            case 2:
                return "已关闭";
            default:
                return "待审核";
        }
    }

    /* ---------------- getter / setter ---------------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getBuddyType() { return buddyType; }
    public void setBuddyType(Integer buddyType) { this.buddyType = buddyType; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getTimeDesc() { return timeDesc; }
    public void setTimeDesc(String timeDesc) { this.timeDesc = timeDesc; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Integer getGenderLimit() { return genderLimit; }
    public void setGenderLimit(Integer genderLimit) { this.genderLimit = genderLimit; }
    public Integer getQuota() { return quota; }
    public void setQuota(Integer quota) { this.quota = quota; }
    public Integer getFeeMode() { return feeMode; }
    public void setFeeMode(Integer feeMode) { this.feeMode = feeMode; }
    public Integer getMatched() { return matched; }
    public void setMatched(Integer matched) { this.matched = matched; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getApplyCnt() { return applyCnt; }
    public void setApplyCnt(Integer applyCnt) { this.applyCnt = applyCnt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isApplied() { return applied; }
    public void setApplied(boolean applied) { this.applied = applied; }
    public Integer getApplyStatus() { return applyStatus; }
    public void setApplyStatus(Integer applyStatus) { this.applyStatus = applyStatus; }
    public boolean isContactUnlocked() { return contactUnlocked; }
    public void setContactUnlocked(boolean contactUnlocked) { this.contactUnlocked = contactUnlocked; }
    public boolean isFavorited() { return favorited; }
    public void setFavorited(boolean favorited) { this.favorited = favorited; }
}
