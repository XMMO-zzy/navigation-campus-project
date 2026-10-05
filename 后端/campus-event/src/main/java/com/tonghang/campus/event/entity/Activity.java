package com.tonghang.campus.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 活动（《12-数据库设计文档》§2.5 activity）。
 */
@TableName("activity")
public class Activity {

    /** 状态：待审 */
    public static final int STATUS_PENDING = 0;
    /** 状态：报名中 */
    public static final int STATUS_ENROLLING = 1;
    /** 状态：进行中 */
    public static final int STATUS_ONGOING = 2;
    /** 状态：已结束 */
    public static final int STATUS_ENDED = 3;
    /** 状态：已取消 */
    public static final int STATUS_CANCELED = 4;

    /** 分类：校园活动 */
    public static final int CATEGORY_CAMPUS = 1;
    /** 分类：学术讲座 */
    public static final int CATEGORY_LECTURE = 2;
    /** 分类：学术调研 */
    public static final int CATEGORY_RESEARCH = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者（须认证用户） */
    private Long publisherId;
    private String title;
    private String cover;
    private String content;
    /** 1 校园活动 2 学术讲座 3 学术调研 */
    private Integer category;
    private String organizer;
    private String contact;
    private String place;
    /** 人数上限 */
    private Integer capacity;
    private Integer enrolledCnt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime enrollStart;
    private LocalDateTime enrollDeadline;
    /** 报名是否需审核 */
    private Integer needReview;
    private String requirement;
    private String rule;
    /** 0 待审 1 报名中 2 进行中 3 已结束 4 已取消 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPublisherId() { return publisherId; }
    public void setPublisherId(Long publisherId) { this.publisherId = publisherId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
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
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
