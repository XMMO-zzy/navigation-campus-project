package com.tonghang.campus.account.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户隐私设置（《12-数据库设计文档》§2.1 user_privacy）。
 */
@TableName("user_privacy")
public class UserPrivacy {

    /** 0 公开 1 仅关注 2 私密 */
    public static final int SCOPE_PUBLIC = 0;
    public static final int SCOPE_FOLLOWERS = 1;
    public static final int SCOPE_PRIVATE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer profileScope;
    private Integer postScope;
    private Integer contactScope;
    /** 0 关闭 1 开启 */
    private Integer recommendEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /* ---------------- getter / setter ---------------- */

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getProfileScope() {
        return profileScope;
    }

    public void setProfileScope(Integer profileScope) {
        this.profileScope = profileScope;
    }

    public Integer getPostScope() {
        return postScope;
    }

    public void setPostScope(Integer postScope) {
        this.postScope = postScope;
    }

    public Integer getContactScope() {
        return contactScope;
    }

    public void setContactScope(Integer contactScope) {
        this.contactScope = contactScope;
    }

    public Integer getRecommendEnabled() {
        return recommendEnabled;
    }

    public void setRecommendEnabled(Integer recommendEnabled) {
        this.recommendEnabled = recommendEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
