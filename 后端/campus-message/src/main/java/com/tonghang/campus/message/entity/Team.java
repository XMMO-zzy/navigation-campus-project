package com.tonghang.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 队伍（《12-数据库设计文档》§2.3 team）。
 */
@TableName("team")
public class Team {

    /** 业务类型：活动 */
    public static final int BIZ_ACTIVITY = 3;
    /** 业务类型：竞赛 */
    public static final int BIZ_COMPETITION = 4;

    /** 状态：招募中 */
    public static final int STATUS_RECRUITING = 0;
    /** 状态：已满 */
    public static final int STATUS_FULL = 1;
    /** 状态：已关闭 */
    public static final int STATUS_CLOSED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Long leaderId;
    /** 3 活动 4 竞赛 */
    private Integer bizType;
    private Long bizId;
    private Integer quota;
    private String announcement;
    private String slogan;
    /** JSON：分工计划 */
    private String dutyPlan;
    private String progress;
    /** 0 招募中 1 已满 2 已关闭 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getLeaderId() { return leaderId; }
    public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
    public Integer getBizType() { return bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Integer getQuota() { return quota; }
    public void setQuota(Integer quota) { this.quota = quota; }
    public String getAnnouncement() { return announcement; }
    public void setAnnouncement(String announcement) { this.announcement = announcement; }
    public String getSlogan() { return slogan; }
    public void setSlogan(String slogan) { this.slogan = slogan; }
    public String getDutyPlan() { return dutyPlan; }
    public void setDutyPlan(String dutyPlan) { this.dutyPlan = dutyPlan; }
    public String getProgress() { return progress; }
    public void setProgress(String progress) { this.progress = progress; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
