package com.tonghang.campus.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 活动报名（《12-数据库设计文档》§2.5 activity_enroll）。
 */
@TableName("activity_enroll")
public class ActivityEnroll {

    /** 状态：待审核 */
    public static final int STATUS_PENDING = 0;
    /** 状态：通过 */
    public static final int STATUS_PASSED = 1;
    /** 状态：拒绝 */
    public static final int STATUS_REJECTED = 2;
    /** 状态：已签到 */
    public static final int STATUS_SIGNED = 3;
    /** 状态：已取消 */
    public static final int STATUS_CANCELED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;
    private Long userId;
    /** 报名姓名 */
    private String name;
    /** 联系手机号 */
    private String phone;
    /** 备注 */
    private String applyMsg;
    /** 0 待审核 1 通过 2 拒绝 3 已签到 4 已取消 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getApplyMsg() { return applyMsg; }
    public void setApplyMsg(String applyMsg) { this.applyMsg = applyMsg; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
