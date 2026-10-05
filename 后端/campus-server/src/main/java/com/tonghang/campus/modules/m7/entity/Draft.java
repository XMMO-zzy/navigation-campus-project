package com.tonghang.campus.modules.m7.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 草稿（《12-数据库设计文档》§2.7 draft）。
 *
 * <p>来源：《13-API接口文档》§3.7「GET/PUT /drafts/{type} 草稿读取/保存（五类，JSON payload）」
 * — FR-M2-10；唯一键 {@code uk_draft(user_id, draft_type)}。
 */
@TableName("draft")
public class Draft {

    /** 草稿类型：动态 */
    public static final int TYPE_POST = 1;
    /** 草稿类型：商品 */
    public static final int TYPE_GOODS = 2;
    /** 草稿类型：活动 */
    public static final int TYPE_ACTIVITY = 3;
    /** 草稿类型：竞赛 */
    public static final int TYPE_COMPETITION = 4;
    /** 草稿类型：搭子 */
    public static final int TYPE_BUDDY = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 1 动态 2 商品 3 活动 4 竞赛 5 搭子 */
    private Integer draftType;
    /** JSON payload */
    private String payload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getDraftType() { return draftType; }
    public void setDraftType(Integer draftType) { this.draftType = draftType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
