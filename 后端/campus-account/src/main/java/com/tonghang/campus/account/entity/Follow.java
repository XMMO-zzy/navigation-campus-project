package com.tonghang.campus.account.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 关注关系（《12-数据库设计文档》§2.1 follow）。
 *
 * <p>说明：契约缺口——《13》§3 未定义关注写接口，仅有本表与《12》§2.1 的存储定义。
 * 本实体仅用于「关注」页签的数据读取与主页 {@code followed} 标记展示，
 * 写操作（关注/取关）在契约评审前不开放（见框架层 api.js §8 预留区块）。
 */
@TableName("follow")
public class Follow {

    /** target_type：用户 */
    public static final int TARGET_USER = 1;
    /** target_type：话题 */
    public static final int TARGET_TOPIC = 9;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long followerId;
    /** 1 用户 9 话题 */
    private Integer targetType;
    private Long targetId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFollowerId() {
        return followerId;
    }

    public void setFollowerId(Long followerId) {
        this.followerId = followerId;
    }

    public Integer getTargetType() {
        return targetType;
    }

    public void setTargetType(Integer targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
