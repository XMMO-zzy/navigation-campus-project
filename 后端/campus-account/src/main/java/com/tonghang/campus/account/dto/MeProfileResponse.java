package com.tonghang.campus.account.dto;

/**
 * 我的主页头部（《13》§3.1 GET /users/me）：
 * 「资料卡 + 数据概览 + 认证状态」——FR-M7-07。
 */
public class MeProfileResponse {

    private Long id;
    private String nickname;
    private String avatar;
    private Integer gender;
    private String major;
    private String grade;
    private String bio;
    private java.util.List<String> skillTags;
    private java.util.List<String> hobbyTags;
    private String personality;
    private String personality_;

    /** 1 普通用户 2 认证用户 */
    private Integer userType;
    /** 0 未认证 1 认证中 2 已认证 3 未通过 */
    private Integer verifyStatus;
    private String rejectReason;
    private Integer creditScore;
    private String schoolTag;

    /** 数据概览 */
    private Stat stat;

    /** 数据概览：发布/参与/收藏/关注 */
    public static class Stat {
        private int postCount;
        private int goodsCount;
        private int enrollCount;
        private int favoriteCount;
        private int followCount;

        public int getPostCount() {
            return postCount;
        }

        public void setPostCount(int postCount) {
            this.postCount = postCount;
        }

        public int getGoodsCount() {
            return goodsCount;
        }

        public void setGoodsCount(int goodsCount) {
            this.goodsCount = goodsCount;
        }

        public int getEnrollCount() {
            return enrollCount;
        }

        public void setEnrollCount(int enrollCount) {
            this.enrollCount = enrollCount;
        }

        public int getFavoriteCount() {
            return favoriteCount;
        }

        public void setFavoriteCount(int favoriteCount) {
            this.favoriteCount = favoriteCount;
        }

        public int getFollowCount() {
            return followCount;
        }

        public void setFollowCount(int followCount) {
            this.followCount = followCount;
        }
    }

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

    public java.util.List<String> getSkillTags() {
        return skillTags;
    }

    public void setSkillTags(java.util.List<String> skillTags) {
        this.skillTags = skillTags;
    }

    public java.util.List<String> getHobbyTags() {
        return hobbyTags;
    }

    public void setHobbyTags(java.util.List<String> hobbyTags) {
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

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public String getSchoolTag() {
        return schoolTag;
    }

    public void setSchoolTag(String schoolTag) {
        this.schoolTag = schoolTag;
    }

    public Stat getStat() {
        return stat;
    }

    public void setStat(Stat stat) {
        this.stat = stat;
    }
}
