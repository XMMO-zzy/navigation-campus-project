package com.tonghang.campus.account.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 登录凭证（《12-数据库设计文档》§2.1 user_credential）。
 */
@TableName("user_credential")
public class UserCredential {

    /** 凭证类型：微信 openid */
    public static final int TYPE_WX_OPENID = 1;
    /** 凭证类型：手机号 */
    public static final int TYPE_PHONE = 2;
    /** 凭证类型：邮箱 */
    public static final int TYPE_EMAIL = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 1 微信 openid 2 手机号 3 邮箱 */
    private Integer credType;
    /** 凭证（手机号/邮箱 AES 加密） */
    private String identifier;
    private Integer verified;

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

    public Integer getCredType() {
        return credType;
    }

    public void setCredType(Integer credType) {
        this.credType = credType;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Integer getVerified() {
        return verified;
    }

    public void setVerified(Integer verified) {
        this.verified = verified;
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
