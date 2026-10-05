package com.tonghang.campus.account.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户只读视图（跨模块消费入口）。
 *
 * <p>来源：《13-API接口文档》§3.2「user 对象」通用结构：
 * {@code { id, nickname, schoolTag, followed }}，动态/搭子/组队等处复用。
 *
 * <p>规则（《11》§2.1）：其它模块不得直接注入 account 的 Mapper，
 * 一律通过本接口取用户展示信息。
 */
public interface UserFacade {

    /** 单用户展示信息 */
    UserBrief get(Long userId);

    /** 批量用户展示信息（返回 Map，key = userId） */
    Map<Long, UserBrief> getBatch(Collection<Long> userIds);

    /**
     * 用户展示信息（含隐私脱敏后的扩展字段）。
     *
     * @param viewerId 访问者 id（游客传 null），用于判定资料可见范围
     * @param targetId 被访问者 id
     * @return 可见则返回，不可见返回受限视图（nickname 打码）
     */
    UserBrief getWithPrivacy(Long viewerId, Long targetId);

    /** 认证用户判定（user_type=2，见《10》FR-M1-06 四态之 2） */
    boolean isVerified(Long userId);

    /** 用户昵称，用于通知文案 */
    String nicknameOf(Long userId);

    /** 取用户 id 列表对应的昵称列表（顺序一致） */
    List<String> nicknamesOf(Collection<Long> userIds);

    /**
     * 用户简要展示结构。
     */
    class UserBrief {
        private Long id;
        private String nickname;
        private String avatar;
        private String schoolTag;
        private String major;
        private String bio;
        private Integer gender;
        private Integer userType;
        private Integer verifyStatus;
        private Integer creditScore;
        private List<String> skills;
        /** 当前访问者是否已关注 TA（契约缺口期固定 false） */
        private boolean followed;
        /** 资料受限（隐私设置不可见） */
        private boolean restricted;

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

        public String getBio() {
            return bio;
        }

        public void setBio(String bio) {
            this.bio = bio;
        }

        public Integer getGender() {
            return gender;
        }

        public void setGender(Integer gender) {
            this.gender = gender;
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

        public List<String> getSkills() {
            return skills;
        }

        public void setSkills(List<String> skills) {
            this.skills = skills;
        }

        public boolean isFollowed() {
            return followed;
        }

        public void setFollowed(boolean followed) {
            this.followed = followed;
        }

        public boolean isRestricted() {
            return restricted;
        }

        public void setRestricted(boolean restricted) {
            this.restricted = restricted;
        }
    }
}
