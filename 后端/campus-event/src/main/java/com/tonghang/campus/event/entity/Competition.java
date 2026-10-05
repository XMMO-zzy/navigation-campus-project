package com.tonghang.campus.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 竞赛（《12-数据库设计文档》§2.5 competition）。
 */
@TableName("competition")
public class Competition {

    /** 状态：报名中 */
    public static final int STATUS_ENROLLING = 1;
    /** 状态：进行中 */
    public static final int STATUS_ONGOING = 2;
    /** 状态：已结束 */
    public static final int STATUS_ENDED = 3;
    /** 状态：已取消 */
    public static final int STATUS_CANCELED = 4;

    /** 级别：校级 */
    public static final int LEVEL_SCHOOL = 1;
    /** 级别：省级 */
    public static final int LEVEL_PROVINCE = 2;
    /** 级别：国家级 */
    public static final int LEVEL_NATION = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布者（须认证用户） */
    private Long publisherId;
    private String title;
    private String cover;
    private String content;
    /** 1 校级 2 省级 3 国家级 */
    private Integer level;
    private String organizer;
    private String contact;
    private String place;
    private Integer capacity;
    private Integer enrolledCnt;
    private String requirement;
    /** 奖项设置 */
    private String awards;
    /** 赛程与规则 */
    private String rule;
    private LocalDateTime enrollStart;
    private LocalDateTime enrollDeadline;
    /** 初赛时间 */
    private LocalDateTime preliminaryAt;
    /** 决赛时间 */
    private LocalDateTime finalAt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** 1 报名中 2 进行中 3 已结束 4 已取消 */
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
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
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
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public String getAwards() { return awards; }
    public void setAwards(String awards) { this.awards = awards; }
    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }
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
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
