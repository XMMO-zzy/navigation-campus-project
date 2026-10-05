package com.tonghang.campus.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.notify.NotifySender;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.message.entity.Team;
import com.tonghang.campus.message.entity.TeamApply;
import com.tonghang.campus.message.entity.TeamMember;
import com.tonghang.campus.message.mapper.TeamApplyMapper;
import com.tonghang.campus.message.mapper.TeamMapper;
import com.tonghang.campus.message.mapper.TeamMemberMapper;
import com.tonghang.campus.message.support.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组队与申请中心服务（M3）。
 *
 * <p>来源：《13-API接口文档》§3.3：
 * <ul>
 *   <li>GET /team-applies?direction= 申请中心 — FR-M3-04</li>
 *   <li>PUT /team-applies/{id} 处理 agree/reject/ask/withdraw — FR-M3-04</li>
 *   <li>POST /teams 创建队伍 — US-03a</li>
 *   <li>GET /teams/{id} 队伍详情（成员/分工/公告/进度）— FR-M3-04</li>
 *   <li>PUT /teams/{id}/members/{uid} 移除成员 / 转让队长 — FR-M5-01</li>
 * </ul>
 *
 * <p>另：《13》§3.3 未定义「申请加入队伍」写入接口，但前端组队入口需要；
 * 按契约缺口处理——保留 {@code POST /teams/{id}/apply} 的语义实现，
 * 并在交付说明中登记为待评审项（不臆造字段，仅用表内已有列）。
 */
@Service
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamApplyMapper applyMapper;
    private final UserFacade userFacade;
    private final NotifySender notifySender;

    public TeamService(TeamMapper teamMapper,
                       TeamMemberMapper memberMapper,
                       TeamApplyMapper applyMapper,
                       UserFacade userFacade,
                       NotifySender notifySender) {
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.applyMapper = applyMapper;
        this.userFacade = userFacade;
        this.notifySender = notifySender;
    }

    /* ==================== 申请中心（FR-M3-04） ==================== */

    /**
     * 申请中心（GET /team-applies?direction=received|sent|invited）。
     *
     * <p>方向口径（《13》§4 示例 direction=received 为「收到的申请」）：
     * <pre>
     *   received  我作为队长收到的申请  → team_apply.user_id = 申请人，team_id ∈ 我任队长的队伍
     *   sent      我发出的申请          → team_apply.user_id = 我 且 direction = 1
     *   invited   我收到的邀请          → team_apply.user_id = 我 且 direction = 2
     * </pre>
     */
    public PageResult<Map<String, Object>> applies(Long userId, String direction,
                                                   Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        String dir = direction == null || direction.trim().isEmpty() ? "received" : direction.trim();

        List<Long> teamIds = leaderTeamIds(userId);
        LambdaQueryWrapper<TeamApply> w = new LambdaQueryWrapper<>();
        boolean empty = false;

        switch (dir) {
            case "sent":
                w.eq(TeamApply::getUserId, userId).eq(TeamApply::getDirection, TeamApply.DIRECTION_SENT);
                break;
            case "invited":
                w.eq(TeamApply::getUserId, userId).eq(TeamApply::getDirection, TeamApply.DIRECTION_INVITED);
                break;
            case "received":
            default:
                if (teamIds.isEmpty()) {
                    empty = true;
                } else {
                    w.in(TeamApply::getTeamId, teamIds)
                            .eq(TeamApply::getDirection, TeamApply.DIRECTION_SENT)
                            .ne(TeamApply::getUserId, userId);
                }
                break;
        }
        if (empty) {
            return PageResult.empty(p, s);
        }
        w.orderByDesc(TeamApply::getId);

        Page<TeamApply> pageObj = new Page<>(p, s);
        applyMapper.selectPage(pageObj, w);

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (TeamApply a : pageObj.getRecords()) {
            list.add(toApplyItem(a));
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /**
     * 处理申请（PUT /team-applies/{id}，action=agree/reject/ask/withdraw）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleApply(Long userId, Long applyId, String action, String message) {
        TeamApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw BizException.notFound("申请记录不存在");
        }
        Team team = teamMapper.selectById(apply.getTeamId());
        if (team == null) {
            throw BizException.notFound("队伍不存在");
        }
        boolean isLeader = team.getLeaderId().equals(userId);
        boolean isApplicant = apply.getUserId().equals(userId);

        if (action == null || action.trim().isEmpty()) {
            throw BizException.param("缺少 action");
        }

        switch (action.trim()) {
            case "agree": {
                requireLeader(isLeader);
                requirePending(apply);
                if (memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, team.getId())
                        .eq(TeamMember::getUserId, apply.getUserId())) > 0) {
                    throw new BizException(ErrorCode.DUPLICATE, "该成员已在队伍中");
                }
                if (isFull(team)) {
                    throw new BizException(ErrorCode.STATUS_CONFLICT, "队伍人数已满");
                }
                apply.setStatus(TeamApply.STATUS_PASSED);
                apply.setReplyMsg(message);
                applyMapper.updateById(apply);

                TeamMember tm = new TeamMember();
                tm.setTeamId(team.getId());
                tm.setUserId(apply.getUserId());
                tm.setRole(TeamMember.ROLE_MEMBER);
                memberMapper.insert(tm);

                refreshFullStatus(team);
                notifySender.send(apply.getUserId(), Enums.NotifyBiz.TEAM,
                        "组队申请已通过", "你已加入「" + team.getName() + "」",
                        Enums.TargetType.TEAM, team.getId(), "team-agree:" + applyId);
                break;
            }
            case "reject": {
                requireLeader(isLeader);
                requirePending(apply);
                apply.setStatus(TeamApply.STATUS_REJECTED);
                apply.setReplyMsg(message);
                applyMapper.updateById(apply);
                notifySender.send(apply.getUserId(), Enums.NotifyBiz.TEAM,
                        "组队申请未通过", "「" + team.getName() + "」暂未通过你的申请",
                        Enums.TargetType.TEAM, team.getId(), "team-reject:" + applyId);
                break;
            }
            case "ask": {
                requireLeader(isLeader);
                requirePending(apply);
                if (message == null || message.trim().isEmpty()) {
                    throw BizException.param("请填写补充询问内容");
                }
                apply.setStatus(TeamApply.STATUS_ASKING);
                apply.setReplyMsg(message);
                applyMapper.updateById(apply);
                notifySender.send(apply.getUserId(), Enums.NotifyBiz.TEAM,
                        "队长向你追问", message, Enums.TargetType.TEAM, team.getId(),
                        "team-ask:" + applyId);
                break;
            }
            case "withdraw": {
                if (!isApplicant) {
                    throw new BizException(ErrorCode.FORBIDDEN, "只能撤回自己的申请");
                }
                requirePending(apply);
                apply.setStatus(TeamApply.STATUS_WITHDRAWN);
                applyMapper.updateById(apply);
                break;
            }
            default:
                throw BizException.param("action 取值需为 agree/reject/ask/withdraw");
        }

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("applyId", apply.getId());
        data.put("status", apply.getStatus());
        data.put("statusText", applyStatusText(apply.getStatus()));
        return data;
    }

    /* ==================== 队伍 ==================== */

    /** 创建队伍（POST /teams，US-03a） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String name = str(body.get("name"));
        if (name == null || name.length() > 64) {
            throw BizException.param("队伍名称需为 1~64 个字符");
        }
        Integer bizType = intOrNull(body.get("bizType"));
        if (bizType == null
                || (bizType != Team.BIZ_ACTIVITY && bizType != Team.BIZ_COMPETITION)) {
            throw BizException.param("bizType 取值需为 3（活动）或 4（竞赛）");
        }
        int quota = intOf(body.get("quota"), 5);
        if (quota < 1 || quota > 100) {
            throw BizException.param("队伍人数上限需为 1~100");
        }

        Team team = new Team();
        team.setName(name);
        team.setLeaderId(userId);
        team.setBizType(bizType);
        team.setBizId(longOrNull(body.get("bizId")));
        team.setQuota(quota);
        team.setSlogan(str(body.get("slogan")));
        team.setAnnouncement(str(body.get("announcement")));
        team.setProgress(str(body.get("progress")));
        team.setDutyPlan(body.get("dutyPlan") == null ? null : JsonUtil.toJson(body.get("dutyPlan")));
        team.setStatus(Team.STATUS_RECRUITING);
        teamMapper.insert(team);

        // 队长自动入队
        TeamMember leader = new TeamMember();
        leader.setTeamId(team.getId());
        leader.setUserId(userId);
        leader.setRole(TeamMember.ROLE_LEADER);
        leader.setDuty("队长");
        memberMapper.insert(leader);

        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("teamId", team.getId());
        data.put("team", teamItem(team));
        return data;
    }

    /** 队伍详情（GET /teams/{id}） */
    public Map<String, Object> detail(Long viewerId, Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw BizException.notFound("队伍不存在");
        }
        Map<String, Object> data = teamItem(team);

        List<TeamMember> members = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .orderByAsc(TeamMember::getRole)
                .orderByAsc(TeamMember::getId));
        List<Map<String, Object>> memberList = new ArrayList<>(members.size());
        for (TeamMember tm : members) {
            Map<String, Object> m = new LinkedHashMap<>(6);
            UserFacade.UserBrief u = userFacade.get(tm.getUserId());
            m.put("userId", tm.getUserId());
            m.put("nickname", u == null ? "用户" + tm.getUserId() : u.getNickname());
            m.put("avatar", u == null ? null : u.getAvatar());
            m.put("schoolTag", u == null ? null : u.getSchoolTag());
            m.put("role", tm.getRole());
            m.put("roleText", tm.getRole() != null && tm.getRole() == TeamMember.ROLE_LEADER ? "队长" : "成员");
            m.put("duty", tm.getDuty());
            m.put("joinedAt", tm.getJoinedAt());
            memberList.add(m);
        }
        data.put("members", memberList);
        data.put("current", members.size());
        data.put("isLeader", viewerId != null && team.getLeaderId().equals(viewerId));
        data.put("joined", viewerId != null && containsMember(members, viewerId));
        return data;
    }

    /**
     * 移除成员 / 转让队长（PUT /teams/{id}/members/{uid}，FR-M5-01）。
     *
     * <p>约定：{@code { "action": "remove"|"transfer"|"setDuty", "duty": "" }}。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateMember(Long userId, Long teamId, Long targetUid,
                                            Map<String, Object> body) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw BizException.notFound("队伍不存在");
        }
        if (!team.getLeaderId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅队长可管理成员");
        }
        TeamMember target = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, targetUid)
                .last("LIMIT 1"));
        if (target == null) {
            throw BizException.notFound("该成员不在队伍中");
        }
        String action = body == null || body.get("action") == null
                ? "remove" : String.valueOf(body.get("action"));

        switch (action) {
            case "transfer": {
                if (target.getRole() != null && target.getRole() == TeamMember.ROLE_LEADER) {
                    throw new BizException(ErrorCode.DUPLICATE, "对方已是队长");
                }
                TeamMember self = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId)
                        .last("LIMIT 1"));
                if (self != null) {
                    self.setRole(TeamMember.ROLE_MEMBER);
                    memberMapper.updateById(self);
                }
                target.setRole(TeamMember.ROLE_LEADER);
                memberMapper.updateById(target);
                team.setLeaderId(targetUid);
                teamMapper.updateById(team);
                notifySender.send(targetUid, Enums.NotifyBiz.TEAM,
                        "你已成为队长", "你已接任「" + team.getName() + "」队长",
                        Enums.TargetType.TEAM, teamId, "team-transfer:" + teamId);
                break;
            }
            case "setDuty": {
                target.setDuty(body == null || body.get("duty") == null
                        ? null : String.valueOf(body.get("duty")));
                memberMapper.updateById(target);
                break;
            }
            case "remove":
            default: {
                if (target.getUserId().equals(team.getLeaderId())) {
                    throw new BizException(ErrorCode.STATUS_CONFLICT, "队长不能被移除，请先转让队长");
                }
                memberMapper.deleteById(target.getId());
                refreshFullStatus(team);
                notifySender.send(targetUid, Enums.NotifyBiz.TEAM,
                        "你已被移出队伍", "你已被移出「" + team.getName() + "」",
                        Enums.TargetType.TEAM, teamId, "team-remove:" + teamId + ":" + targetUid);
                break;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("teamId", teamId);
        data.put("userId", targetUid);
        data.put("action", action);
        return data;
    }

    /**
     * 申请加入队伍。
     *
     * <p>契约缺口登记：《13》§3.3 未列出该写入接口（仅有 team-applies 查询与处理）；
     * 为支撑「活动/竞赛组队」主链路，此处按 team_apply 表既有列实现，
     * status/字段不做臆造扩展，交付说明中标为待契约评审项。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(Long userId, Long teamId, Map<String, Object> body) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw BizException.notFound("队伍不存在");
        }
        if (team.getLeaderId().equals(userId)) {
            throw new BizException(ErrorCode.DUPLICATE, "你是队长，无需申请");
        }
        if (isFull(team)) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "队伍人数已满");
        }
        if (memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)) > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "你已在队伍中");
        }
        TeamApply exist = applyMapper.selectOne(new LambdaQueryWrapper<TeamApply>()
                .eq(TeamApply::getTeamId, teamId)
                .eq(TeamApply::getUserId, userId)
                .in(TeamApply::getStatus, TeamApply.STATUS_PENDING, TeamApply.STATUS_ASKING)
                .last("LIMIT 1"));
        if (exist != null) {
            throw new BizException(ErrorCode.DUPLICATE, "申请处理中，请勿重复提交");
        }

        TeamApply apply = new TeamApply();
        apply.setTeamId(teamId);
        apply.setUserId(userId);
        apply.setDirection(TeamApply.DIRECTION_SENT);
        apply.setMessage(body == null || body.get("message") == null
                ? null : String.valueOf(body.get("message")));
        apply.setSkills(JsonUtil.toJson(userFacade.get(userId) == null
                ? Collections.emptyList() : userFacade.get(userId).getSkills()));
        apply.setStatus(TeamApply.STATUS_PENDING);
        applyMapper.insert(apply);

        notifySender.send(team.getLeaderId(), Enums.NotifyBiz.TEAM,
                "收到新的组队申请", userFacade.nicknameOf(userId) + " 申请加入「" + team.getName() + "」",
                Enums.TargetType.TEAM, teamId, "team-apply:" + apply.getId());

        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("applyId", apply.getId());
        data.put("status", apply.getStatus());
        return data;
    }

    /** 我参与的队伍 id 列表（供活动/竞赛报名关联队伍） */
    public List<Long> myTeamIds(Long userId) {
        List<TeamMember> rows = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .select(TeamMember::getTeamId));
        List<Long> ids = new ArrayList<>(rows.size());
        for (TeamMember tm : rows) {
            ids.add(tm.getTeamId());
        }
        return ids;
    }

    /** 我参与的队伍列表（供组队入口展示） */
    public List<Map<String, Object>> myTeams(Long userId) {
        List<Long> ids = myTeamIds(userId);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Team> teams = teamMapper.selectBatchIds(ids);
        List<Map<String, Object>> list = new ArrayList<>(teams.size());
        for (Team t : teams) {
            list.add(teamItem(t));
        }
        return list;
    }

    /* ==================== 内部工具 ==================== */

    private void requireLeader(boolean isLeader) {
        if (!isLeader) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅队长可处理申请");
        }
    }

    private void requirePending(TeamApply apply) {
        if (apply.getStatus() == null
                || (apply.getStatus() != TeamApply.STATUS_PENDING
                && apply.getStatus() != TeamApply.STATUS_ASKING)) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "该申请已处理，不能重复操作");
        }
    }

    private List<Long> leaderTeamIds(Long userId) {
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getLeaderId, userId)
                .select(Team::getId));
        List<Long> ids = new ArrayList<>(teams.size());
        for (Team t : teams) {
            ids.add(t.getId());
        }
        return ids;
    }

    private boolean containsMember(List<TeamMember> members, Long userId) {
        for (TeamMember tm : members) {
            if (tm.getUserId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFull(Team team) {
        long cnt = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, team.getId()));
        return team.getQuota() != null && cnt >= team.getQuota();
    }

    /** 满员则置为已满，否则回招募中（未关闭状态才动） */
    private void refreshFullStatus(Team team) {
        if (team.getStatus() != null && team.getStatus() == Team.STATUS_CLOSED) {
            return;
        }
        team.setStatus(isFull(team) ? Team.STATUS_FULL : Team.STATUS_RECRUITING);
        teamMapper.updateById(team);
    }

    private Map<String, Object> teamItem(Team t) {
        Map<String, Object> m = new LinkedHashMap<>(12);
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("leaderId", t.getLeaderId());
        m.put("bizType", t.getBizType());
        m.put("bizTypeText", t.getBizType() != null && t.getBizType() == Team.BIZ_ACTIVITY ? "活动" : "竞赛");
        m.put("bizId", t.getBizId());
        m.put("bizTitle", bizTitle(t));
        m.put("quota", t.getQuota());
        m.put("announcement", t.getAnnouncement());
        m.put("slogan", t.getSlogan());
        m.put("progress", t.getProgress());
        m.put("dutyPlan", JsonUtil.toJson(t.getDutyPlan()));
        m.put("status", t.getStatus());
        m.put("statusText", teamStatusText(t.getStatus()));
        m.put("createdAt", t.getCreatedAt());
        return m;
    }

    /** 关联活动/竞赛标题：由 event 模块 Facade 提供，未装配时留给前端按 bizId 跳转 */
    private String bizTitle(Team t) {
        return null;
    }

    private Map<String, Object> toApplyItem(TeamApply a) {
        Map<String, Object> item = new LinkedHashMap<>(12);
        item.put("id", a.getId());
        item.put("direction", a.getDirection());

        Team team = teamMapper.selectById(a.getTeamId());
        if (team != null) {
            Map<String, Object> t = new LinkedHashMap<>(6);
            t.put("id", team.getId());
            t.put("name", team.getName());
            t.put("bizType", team.getBizType());
            t.put("bizTitle", bizTitle(team));
            t.put("quota", team.getQuota());
            t.put("current", memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                    .eq(TeamMember::getTeamId, team.getId())));
            item.put("team", t);
        }

        UserFacade.UserBrief u = userFacade.get(a.getUserId());
        Map<String, Object> user = new LinkedHashMap<>(6);
        if (u == null) {
            user.put("id", a.getUserId());
            user.put("nickname", "用户" + a.getUserId());
            user.put("skills", Collections.emptyList());
        } else {
            user.put("id", u.getId());
            user.put("nickname", u.getNickname());
            user.put("avatar", u.getAvatar());
            user.put("schoolTag", u.getSchoolTag());
            user.put("skills", u.getSkills());
            user.put("bio", u.getBio());
        }
        item.put("user", user);

        item.put("message", a.getMessage());
        item.put("replyMsg", a.getReplyMsg());
        item.put("status", a.getStatus());
        item.put("statusText", applyStatusText(a.getStatus()));
        item.put("createdAt", a.getCreatedAt());
        return item;
    }

    private String applyStatusText(Integer status) {
        if (status == null) {
            return "待处理";
        }
        switch (status) {
            case TeamApply.STATUS_PASSED:
                return "已通过";
            case TeamApply.STATUS_REJECTED:
                return "已拒绝";
            case TeamApply.STATUS_WITHDRAWN:
                return "已撤回";
            case TeamApply.STATUS_ASKING:
                return "补充询问";
            default:
                return "待处理";
        }
    }

    private String teamStatusText(Integer status) {
        if (status == null) {
            return "招募中";
        }
        switch (status) {
            case Team.STATUS_FULL:
                return "已满";
            case Team.STATUS_CLOSED:
                return "已关闭";
            default:
                return "招募中";
        }
    }

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer intOf(Object v, int fallback) {
        Integer i = intOrNull(v);
        return i == null ? fallback : i;
    }

    private Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
