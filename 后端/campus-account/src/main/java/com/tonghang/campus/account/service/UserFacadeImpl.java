package com.tonghang.campus.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.account.entity.Blacklist;
import com.tonghang.campus.account.entity.User;
import com.tonghang.campus.account.entity.UserPrivacy;
import com.tonghang.campus.account.mapper.BlacklistMapper;
import com.tonghang.campus.account.mapper.UserMapper;
import com.tonghang.campus.account.mapper.UserPrivacyMapper;
import com.tonghang.campus.account.support.JsonUtil;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.security.CurrentUserProvider;
import com.tonghang.campus.common.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户只读视图实现（M1）。
 *
 * <p>同时实现 {@link CurrentUserProvider}，为权限切面提供库中实时认证快照。
 */
@Service
public class UserFacadeImpl implements UserFacade, CurrentUserProvider {

    /** 学校 id 与展示名映射（试点固定 1，见《12》§2.1 user.school_id） */
    private static final Map<Long, String> SCHOOL_TAGS;

    static {
        Map<Long, String> m = new HashMap<>(4);
        m.put(1L, "通航学院");
        SCHOOL_TAGS = Collections.unmodifiableMap(m);
    }

    private final UserMapper userMapper;
    private final UserPrivacyMapper privacyMapper;
    private final BlacklistMapper blacklistMapper;

    public UserFacadeImpl(UserMapper userMapper,
                          UserPrivacyMapper privacyMapper,
                          BlacklistMapper blacklistMapper) {
        this.userMapper = userMapper;
        this.privacyMapper = privacyMapper;
        this.blacklistMapper = blacklistMapper;
    }

    /* ================= CurrentUserProvider ================= */

    @Override
    public LoginUser loadRealtime(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return new LoginUser(user.getId(), user.getUserType(), user.getVerifyStatus(),
                com.tonghang.campus.common.security.JwtTokenProvider.TYPE_ACCESS);
    }

    /* ================= UserFacade ================= */

    @Override
    public UserBrief get(Long userId) {
        if (userId == null) {
            return null;
        }
        return toBrief(userMapper.selectById(userId), false);
    }

    @Override
    public Map<Long, UserBrief> getBatch(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, UserBrief> map = new HashMap<>(users.size() * 2);
        for (User u : users) {
            map.put(u.getId(), toBrief(u, false));
        }
        return map;
    }

    @Override
    public UserBrief getWithPrivacy(Long viewerId, Long targetId) {
        User target = targetId == null ? null : userMapper.selectById(targetId);
        if (target == null) {
            return null;
        }
        // 本人 / 被对方拉黑 → 正常返回
        if (viewerId != null && viewerId.equals(targetId)) {
            return toBrief(target, false);
        }
        // 访问者被目标用户屏蔽：按《10》FR-M2-11，互不可见
        if (viewerId != null && isBlocked(targetId, viewerId)) {
            UserBrief restricted = new UserBrief();
            restricted.setId(target.getId());
            restricted.setRestricted(true);
            restricted.setNickname("该用户已设置不可见");
            return restricted;
        }

        UserPrivacy privacy = privacyMapper.selectOne(
                new LambdaQueryWrapper<UserPrivacy>().eq(UserPrivacy::getUserId, targetId).last("LIMIT 1"));
        int scope = privacy == null || privacy.getProfileScope() == null
                ? UserPrivacy.SCOPE_PUBLIC : privacy.getProfileScope();

        boolean visible = scope == UserPrivacy.SCOPE_PUBLIC;
        if (!visible) {
            // 0 公开 / 1 仅关注 / 2 私密；契约缺口期无关注读写接口，故按不可见处理
            UserBrief restricted = new UserBrief();
            restricted.setId(target.getId());
            restricted.setRestricted(true);
            restricted.setNickname(mask(target.getNickname()));
            restricted.setAvatar(target.getAvatarUrl());
            restricted.setSchoolTag(schoolTag(target.getSchoolId()));
            restricted.setUserType(target.getUserType());
            restricted.setVerifyStatus(target.getVerifyStatus());
            return restricted;
        }

        UserBrief brief = toBrief(target, false);
        // 黑名单关系下不展示关注态
        brief.setFollowed(false);
        return brief;
    }

    @Override
    public boolean isVerified(Long userId) {
        if (userId == null) {
            return false;
        }
        User u = userMapper.selectById(userId);
        // 《10》FR-M1-06 四态之 2 已认证 ⇔《11》§3.1 user_type=2
        return u != null && u.getUserType() != null && u.getUserType() == Enums.UserType.VERIFIED;
    }

    @Override
    public String nicknameOf(Long userId) {
        if (userId == null) {
            return "用户";
        }
        User u = userMapper.selectById(userId);
        return u == null || u.getNickname() == null ? "用户" : u.getNickname();
    }

    @Override
    public List<String> nicknamesOf(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, UserBrief> map = getBatch(userIds);
        java.util.ArrayList<String> list = new java.util.ArrayList<>(userIds.size());
        for (Long id : userIds) {
            UserBrief b = map.get(id);
            list.add(b == null ? "用户" : b.getNickname());
        }
        return list;
    }

    /* ================= 内部工具 ================= */

    /** 是否被 blockOwner 拉黑了 blocked（blockOwner 视角） */
    public boolean isBlocked(Long blockOwnerId, Long blockedId) {
        if (blockOwnerId == null || blockedId == null) {
            return false;
        }
        Long cnt = blacklistMapper.selectCount(new LambdaQueryWrapper<Blacklist>()
                .eq(Blacklist::getUserId, blockOwnerId)
                .eq(Blacklist::getBlockedUserId, blockedId));
        return cnt != null && cnt > 0;
    }

    /** 与给定用户存在任一方向拉黑关系（过滤信息流用） */
    public Set<Long> blockedEitherWay(Long viewerId) {
        if (viewerId == null) {
            return Collections.emptySet();
        }
        List<Blacklist> asOwner = blacklistMapper.selectList(
                new LambdaQueryWrapper<Blacklist>().eq(Blacklist::getUserId, viewerId));
        List<Blacklist> asTarget = blacklistMapper.selectList(
                new LambdaQueryWrapper<Blacklist>().eq(Blacklist::getBlockedUserId, viewerId));
        Set<Long> set = new HashSet<>(asOwner.size() + asTarget.size());
        for (Blacklist b : asOwner) {
            set.add(b.getBlockedUserId());
        }
        for (Blacklist b : asTarget) {
            set.add(b.getUserId());
        }
        return set;
    }

    public UserBrief toBrief(User user, boolean followed) {
        if (user == null) {
            return null;
        }
        UserBrief b = new UserBrief();
        b.setId(user.getId());
        b.setNickname(user.getNickname());
        b.setAvatar(user.getAvatarUrl());
        b.setSchoolTag(schoolTag(user.getSchoolId()));
        b.setMajor(user.getMajor());
        b.setBio(user.getBio());
        b.setGender(user.getGender());
        b.setUserType(user.getUserType());
        b.setVerifyStatus(user.getVerifyStatus());
        b.setCreditScore(user.getCreditScore());
        b.setSkills(JsonUtil.toStringList(user.getSkillTags()));
        b.setFollowed(followed);
        b.setRestricted(false);
        return b;
    }

    private String schoolTag(Long schoolId) {
        if (schoolId == null) {
            return SCHOOL_TAGS.get(1L);
        }
        return SCHOOL_TAGS.getOrDefault(schoolId, SCHOOL_TAGS.get(1L));
    }

    /** 昵称打码：保留首尾字符 */
    private String mask(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return "校园用户";
        }
        if (nickname.length() <= 2) {
            return nickname.charAt(0) + "*";
        }
        return nickname.charAt(0) + "**" + nickname.charAt(nickname.length() - 1);
    }
}
