package com.tonghang.campus.account.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户主表（《12-数据库设计文档》§2.1 user）。
 */
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String nickname;
    private String avatarUrl;
    /** 0 未知 1 男 2 女 */
    private Integer gender;
    private Long schoolId;
    private String major;
    private String grade;
    private String bio;
    /** JSON 字符串（技能标签） */
    private String skillTags;
    /** JSON 字符串（爱好标签） */
    private String hobbyTags;
    private String personality;
    /** 1 普通用户 2 认证用户 */
    private Integer userType;
    /** 0 未认证 1 认证中 2 已认证 3 未通过 */
    private Integer verifyStatus;
    private Integer creditScore;
    /** 0 正常 1 禁言 2 封禁 */
    private Integer status;

    /** 软删（注销）：逻辑删除字段，见 application.yml mybatis-plus.global-config */
    @TableLogic(value = "NULL", delval = "NOW()")
    private LocalDateTime deletedAt;

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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getSkillTags() {
        return skillTags;
    }

    public void setSkillTags(String skillTags) {
        this.skillTags = skillTags;
    }

    public String getHobbyTags() {
        return hobbyTags;
    }

    public void setHobbyTags(String hobbyTags) {
        this.hobbyTags = hobbyTags;
    }

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
    }

    public Integer getUserType() {
        return userType;
    }

    public void setUserType(Integer userType) {
        this.userType = userType;
    }

    public Integer getVerifyStatus() {
        return verifyStatus;
    }

    public void setVerifyStatus(Integer verifyStatus) {
        this.verifyStatus = verifyStatus;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
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
