package com.tonghang.campus.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 搭子帖（《12-数据库设计文档》§2.6 buddy_post）。
 */
@TableName("buddy_post")
public class BuddyPost {

    /** 状态：待审 */
    public static final int STATUS_PENDING = 0;
    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;
    /** 状态：已关闭 */
    public static final int STATUS_CLOSED = 2;

    /** 性别限制：不限 */
    public static final int GENDER_NONE = 0;
    /** 性别限制：仅女性（女性专属模式） */
    public static final int GENDER_FEMALE_ONLY = 1;

    /** 已找到 */
    public static final int MATCHED_YES = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    /** 1 学习 2 运动 3 娱乐 4 旅行 5 饭搭子 6 技能 */
    private Integer buddyType;
    /** JSON：兴趣标签 */
    private String tags;
    /** 展示信息（性别/爱好/年龄/性格） */
    private String desc;
    private String place;
    private String timeDesc;
    private String requirement;
    /** JSON：图片 file_object.id */
    private String images;
    /** 联系方式（通过后可见） */
    private String contact;
    /** 0 不限 1 仅女性 */
    private Integer genderLimit;
    /** 人数上限 */
    private Integer quota;
    /** 0 AA 1 请客 */
    private Integer feeMode;
    /** 0 寻找中 1 已找到 */
    private Integer matched;
    /** 0 待审 1 正常 2 已关闭 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getBuddyType() { return buddyType; }
    public void setBuddyType(Integer buddyType) { this.buddyType = buddyType; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getTimeDesc() { return timeDesc; }
    public void setTimeDesc(String timeDesc) { this.timeDesc = timeDesc; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
