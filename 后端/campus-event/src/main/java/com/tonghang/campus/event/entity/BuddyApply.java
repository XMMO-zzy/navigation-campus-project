package com.tonghang.campus.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 搭子申请（《12-数据库设计文档》§2.6 buddy_apply）。
 */
@TableName("buddy_apply")
public class BuddyApply {

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已通过 */
    public static final int STATUS_PASSED = 1;
    /** 状态：已拒绝 */
    public static final int STATUS_REJECTED = 2;
    /** 状态：已撤回 */
    public static final int STATUS_WITHDRAWN = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long buddyId;
    /** 申请人 */
    private Long userId;
    /** 申请说明 */
    private String message;
    /** 0 待处理 1 已通过 2 已拒绝 3 已撤回 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBuddyId() { return buddyId; }
    public void setBuddyId(Long buddyId) { this.buddyId = buddyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
