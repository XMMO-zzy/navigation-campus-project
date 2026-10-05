package com.tonghang.campus.account.dto;

import java.util.List;
import java.util.Map;

/**
 * 他人主页（《13》§3.1 GET /users/{id}；FR-M2-11 受隐私设置约束）。
 */
public class UserProfileResponse {

    private Long id;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String schoolTag;
    private String major;
    private String grade;
    private String bio;
    private List<String> skillTags;
    private Integer userType;
    private Integer verifyStatus;
    private Integer creditScore;

    /** 访问者是否已关注（契约缺口期固定 false） */
    private boolean followed;
    /** 是否为访问者本人 */
    private boolean self;
    /** 资料是否因隐私设置被限制 */
    private boolean restricted;
    private Integer profileScope;

    private Map<String, Object> stat;

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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getSchoolTag() {
        return schoolTag;
    }

    public void setSchoolTag(String schoolTag) {
        this.schoolTag = schoolTag;
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

    public List<String> getSkillTags() {
        return skillTags;
    }

    public void setSkillTags(List<String> skillTags) {
        this.skillTags = skillTags;
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

    public boolean isFollowed() {
        return followed;
    }

    public void setFollowed(boolean followed) {
        this.followed = followed;
    }

    public boolean isSelf() {
        return self;
    }

    public void setSelf(boolean self) {
        this.self = self;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public Integer getProfileScope() {
        return profileScope;
    }

    public void setProfileScope(Integer profileScope) {
        this.profileScope = profileScope;
    }

    public Map<String, Object> getStat() {
        return stat;
    }

    public void setStat(Map<String, Object> stat) {
        this.stat = stat;
    }
}
