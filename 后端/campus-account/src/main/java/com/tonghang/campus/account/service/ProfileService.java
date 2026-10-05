package com.tonghang.campus.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.account.dto.MeProfileResponse;
import com.tonghang.campus.account.dto.UserProfileResponse;
import com.tonghang.campus.account.entity.Blacklist;
import com.tonghang.campus.account.entity.User;
import com.tonghang.campus.account.entity.UserPrivacy;
import com.tonghang.campus.account.mapper.BlacklistMapper;
import com.tonghang.campus.account.mapper.UserMapper;
import com.tonghang.campus.account.mapper.UserPrivacyMapper;
import com.tonghang.campus.account.support.JsonUtil;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户资料与隐私服务（M1 / M7）。
 *
 * <p>来源：《13-API接口文档》§3.1（GET/PUT /users/me、GET /users/{id}）与 §3.7（隐私 / 黑名单）。
 */
@Service
public class ProfileService {

    private final UserMapper userMapper;
    private final UserPrivacyMapper privacyMapper;
    private final BlacklistMapper blacklistMapper;
    private final UserFacade userFacade;

    public ProfileService(UserMapper userMapper,
                          UserPrivacyMapper privacyMapper,
                          BlacklistMapper blacklistMapper,
                          UserFacade userFacade) {
        this.userMapper = userMapper;
        this.privacyMapper = privacyMapper;
        this.blacklistMapper = blacklistMapper;
        this.userFacade = userFacade;
    }

    /* ==================== 我的主页头部（FR-M7-07） ==================== */

    public MeProfileResponse me(Long userId) {
        User user = requireUser(userId);
        MeProfileResponse r = new MeProfileResponse();
        r.setId(user.getId());
        r.setNickname(user.getNickname());
        r.setAvatar(user.getAvatarUrl());
        r.setGender(user.getGender());
        r.setMajor(user.getMajor());
        r.setGrade(user.getGrade());
        r.setBio(user.getBio());
        r.setSkillTags(JsonUtil.toStringList(user.getSkillTags()));
        r.setHobbyTags(JsonUtil.toStringList(user.getHobbyTags()));
        r.setPersonality(user.getPersonality());
        r.setUserType(user.getUserType());
        r.setVerifyStatus(user.getVerifyStatus());
        r.setCreditScore(user.getCreditScore());
        r.setSchoolTag("通航学院");
        r.setStat(new MeProfileResponse.Stat());
        return r;
    }

    /**
     * 编辑资料（FR-M7-07 / PUT /users/me）。
     *
     * <p>字段白名单与《13》§3.1 一致：头像/昵称/简介/专业/技能/爱好。
     */
    @Transactional(rollbackFor = Exception.class)
    public MeProfileResponse updateMe(Long userId, Map<String, Object> body) {
        User user = requireUser(userId);
        if (body != null) {
            if (body.get("nickname") != null) {
                String nickname = String.valueOf(body.get("nickname")).trim();
                if (nickname.isEmpty() || nickname.length() > 32) {
                    throw BizException.param("昵称长度需为 1~32 个字符");
                }
                user.setNickname(nickname);
            }
            if (body.get("avatar") != null) {
                user.setAvatarUrl(String.valueOf(body.get("avatar")));
            }
            if (body.get("gender") != null) {
                int g = Integer.parseInt(String.valueOf(body.get("gender")));
                if (g < 0 || g > 2) {
                    throw BizException.param("性别取值不合法");
                }
                user.setGender(g);
            }
            if (body.get("major") != null) {
                user.setMajor(String.valueOf(body.get("major")));
            }
            if (body.get("grade") != null) {
                user.setGrade(String.valueOf(body.get("grade")));
            }
            if (body.get("bio") != null) {
                String bio = String.valueOf(body.get("bio"));
                if (bio.length() > 200) {
                    throw BizException.param("简介不能超过 200 个字");
                }
                user.setBio(bio);
            }
            if (body.get("skillTags") != null) {
                user.setSkillTags(JsonUtil.toJson(body.get("skillTags")));
            }
            if (body.get("hobbyTags") != null) {
                user.setHobbyTags(JsonUtil.toJson(body.get("hobbyTags")));
            }
            if (body.get("personality") != null) {
                user.setPersonality(String.valueOf(body.get("personality")));
            }
        }
        userMapper.updateById(user);
        return me(userId);
    }

    /* ==================== 他人主页（FR-M2-11） ==================== */

    public UserProfileResponse other(Long viewerId, Long targetId) {
        User user = requireUser(targetId);
        UserFacade.UserBrief brief = userFacade.getWithPrivacy(viewerId, targetId);
        if (brief == null) {
            throw BizException.notFound("用户不存在");
        }

        UserProfileResponse r = new UserProfileResponse();
        r.setId(brief.getId());
        r.setNickname(brief.getNickname());
        r.setAvatar(brief.getAvatar());
        r.setGender(brief.getGender());
        r.setSchoolTag(brief.getSchoolTag());
        r.setMajor(brief.getMajor());
        r.setGrade(user.getGrade());
        r.setBio(brief.getBio());
        r.setSkillTags(brief.getSkills());
        r.setUserType(brief.getUserType());
        r.setVerifyStatus(brief.getVerifyStatus());
        r.setCreditScore(brief.getCreditScore());
        r.setFollowed(brief.isFollowed());
        r.setRestricted(brief.isRestricted());
        r.setSelf(viewerId != null && viewerId.equals(targetId));

        // 隐私：资料可见范围；受限时不返回统计数据
        UserPrivacy privacy = ensurePrivacy(targetId);
        r.setProfileScope(privacy.getProfileScope());
        Map<String, Object> stat = new LinkedHashMap<>(4);
        if (!brief.isRestricted()) {
            stat.put("postCount", 0);
            stat.put("goodsCount", 0);
            stat.put("enrollCount", 0);
        }
        r.setStat(stat);
        return r;
    }

    /* ==================== 隐私设置（FR-M7-06） ==================== */

    public Map<String, Object> getPrivacy(Long userId) {
        UserPrivacy p = ensurePrivacy(userId);
        return privacyToMap(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updatePrivacy(Long userId, Map<String, Object> body) {
        UserPrivacy p = ensurePrivacy(userId);
        if (body != null) {
            if (body.get("profileScope") != null) {
                p.setProfileScope(scopeOf(body.get("profileScope"), "profileScope"));
            }
            if (body.get("postScope") != null) {
                p.setPostScope(scopeOf(body.get("postScope"), "postScope"));
            }
            if (body.get("contactScope") != null) {
                p.setContactScope(scopeOf(body.get("contactScope"), "contactScope"));
            }
            if (body.get("recommendEnabled") != null) {
                int v = Integer.parseInt(String.valueOf(body.get("recommendEnabled")));
                p.setRecommendEnabled(v == 1 ? 1 : 0);
            }
        }
        privacyMapper.updateById(p);
        return privacyToMap(p);
    }

    /* ==================== 黑名单（FR-M7-06 / FR-M2-11） ==================== */

    public Map<String, Object> getBlacklist(Long userId) {
        List<Blacklist> rows = blacklistMapper.selectList(new LambdaQueryWrapper<Blacklist>()
                .eq(Blacklist::getUserId, userId)
                .orderByDesc(Blacklist::getId));
        List<Object> list = new ArrayList<>(rows.size());
        for (Blacklist b : rows) {
            UserFacade.UserBrief brief = userFacade.get(b.getBlockedUserId());
            if (brief == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(4);
            item.put("userId", brief.getId());
            item.put("nickname", brief.getNickname());
            item.put("avatar", brief.getAvatar());
            item.put("blockedAt", b.getCreatedAt());
            list.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("list", list);
        data.put("total", list.size());
        return data;
    }

    /**
     * 更新黑名单（PUT /users/me/blacklist）。
     *
     * <p>约定：{@code { "action": "block"|"unblock", "userId": 123 }}；
     * 文档未定义批量字段结构，此处保持最小可用语义。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateBlacklist(Long userId, Map<String, Object> body) {
        if (body == null || body.get("userId") == null) {
            throw BizException.param("缺少 userId");
        }
        Long target = Long.parseLong(String.valueOf(body.get("userId")));
        if (target.equals(userId)) {
            throw BizException.param("不能屏蔽自己");
        }
        String action = body.get("action") == null ? "block" : String.valueOf(body.get("action"));

        if ("unblock".equalsIgnoreCase(action)) {
            blacklistMapper.delete(new LambdaQueryWrapper<Blacklist>()
                    .eq(Blacklist::getUserId, userId)
                    .eq(Blacklist::getBlockedUserId, target));
        } else {
            Long cnt = blacklistMapper.selectCount(new LambdaQueryWrapper<Blacklist>()
                    .eq(Blacklist::getUserId, userId)
                    .eq(Blacklist::getBlockedUserId, target));
            if (cnt != null && cnt > 0) {
                throw new BizException(ErrorCode.DUPLICATE, "已在黑名单中");
            }
            Blacklist b = new Blacklist();
            b.setUserId(userId);
            b.setBlockedUserId(target);
            blacklistMapper.insert(b);
        }
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("userId", target);
        data.put("blocked", !"unblock".equalsIgnoreCase(action));
        return data;
    }

    /* ==================== 内部工具 ==================== */

    public User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    /** 取隐私设置，不存在则按默认值落库（公开 / 开推荐） */
    @Transactional(rollbackFor = Exception.class)
    public UserPrivacy ensurePrivacy(Long userId) {
        UserPrivacy p = privacyMapper.selectOne(new LambdaQueryWrapper<UserPrivacy>()
                .eq(UserPrivacy::getUserId, userId).last("LIMIT 1"));
        if (p != null) {
            return p;
        }
        p = new UserPrivacy();
        p.setUserId(userId);
        p.setProfileScope(UserPrivacy.SCOPE_PUBLIC);
        p.setPostScope(UserPrivacy.SCOPE_PUBLIC);
        p.setContactScope(UserPrivacy.SCOPE_PUBLIC);
        p.setRecommendEnabled(1);
        privacyMapper.insert(p);
        return p;
    }

    private Map<String, Object> privacyToMap(UserPrivacy p) {
        Map<String, Object> m = new LinkedHashMap<>(6);
        m.put("profileScope", p.getProfileScope());
        m.put("postScope", p.getPostScope());
        m.put("contactScope", p.getContactScope());
        m.put("recommendEnabled", p.getRecommendEnabled());
        m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    private int scopeOf(Object v, String field) {
        int s = Integer.parseInt(String.valueOf(v));
        if (s < 0 || s > 2) {
            throw BizException.param(field + " 取值需为 0/1/2（公开/仅关注/私密）");
        }
        return s;
    }

    /** 供其它模块判断：user 是否为认证用户 */
    public boolean isVerified(Long userId) {
        return userFacade.isVerified(userId);
    }

    /** 供其它模块复用：用户是否被封禁/禁言 */
    public int statusOf(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return Enums.UserType.NORMAL;
        }
        return u.getStatus() == null ? 0 : u.getStatus();
    }
}
