package com.tonghang.campus.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.message.entity.NotifyMessage;
import com.tonghang.campus.message.entity.Team;
import com.tonghang.campus.message.entity.TeamApply;
import com.tonghang.campus.message.mapper.NotifyMessageMapper;
import com.tonghang.campus.message.mapper.TeamApplyMapper;
import com.tonghang.campus.message.mapper.TeamMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页待办聚合服务（M3 / FR-M3-05）。
 *
 * <p>来源：《13-API接口文档》§3.2 GET /todos
 * 「首页重要消息待办（即将截止/报名开始/规则更新/协作待办）」。
 *
 * <p>聚合口径：
 * <pre>
 *   协作待办  我作为队长收到的待处理组队申请（数据可查，全量实现）
 *   即将截止  活动/竞赛报名截止 72h 内（需 event 模块数据，接口保留）
 *   报名开始  收藏的活动/竞赛开始报名（需 event 模块数据，接口保留）
 *   规则更新  平台规则类官方通知（从 NotifyMessage biz=60 平台通知读取）
 * </pre>
 */
@Service
public class TodoService {

    /** 待办项上限（首页卡片展示条数） */
    private static final int MAX_ITEMS = 5;

    private final NotifyMessageMapper notifyMapper;
    private final TeamApplyMapper applyMapper;
    private final TeamMapper teamMapper;
    private final UserFacade userFacade;

    public TodoService(NotifyMessageMapper notifyMapper,
                       TeamApplyMapper applyMapper,
                       TeamMapper teamMapper,
                       UserFacade userFacade) {
        this.notifyMapper = notifyMapper;
        this.applyMapper = applyMapper;
        this.teamMapper = teamMapper;
        this.userFacade = userFacade;
    }

    /** 首页待办（FR-M3-05） */
    public Map<String, Object> todos(Long userId) {
        List<Map<String, Object>> items = new ArrayList<>();

        // 1) 协作待办：我任队长的队伍中待处理的申请
        items.addAll(pendingTeamApplies(userId));

        // 2) 平台规则更新：未读的平台/系统通知（biz 40 / 60）
        items.addAll(ruleUpdates(userId));

        // 3) 即将截止 / 报名开始：活动竞赛域数据，由 event 模块补齐（此处保留空位）

        List<Map<String, Object>> top = items.size() > MAX_ITEMS ? items.subList(0, MAX_ITEMS) : items;

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("total", items.size());
        data.put("list", top);
        data.put("unreadMessage", unreadOf(userId));
        return data;
    }

    private List<Map<String, Object>> pendingTeamApplies(Long userId) {
        List<Team> myTeams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getLeaderId, userId)
                .select(Team::getId, Team::getName));
        List<Map<String, Object>> list = new ArrayList<>();
        if (myTeams.isEmpty()) {
            return list;
        }
        List<Long> teamIds = new ArrayList<>(myTeams.size());
        Map<Long, String> nameMap = new LinkedHashMap<>();
        for (Team t : myTeams) {
            teamIds.add(t.getId());
            nameMap.put(t.getId(), t.getName());
        }
        List<TeamApply> applies = applyMapper.selectList(new LambdaQueryWrapper<TeamApply>()
                .in(TeamApply::getTeamId, teamIds)
                .in(TeamApply::getStatus, TeamApply.STATUS_PENDING, TeamApply.STATUS_ASKING)
                .orderByDesc(TeamApply::getId)
                .last("LIMIT " + MAX_ITEMS));
        for (TeamApply a : applies) {
            Map<String, Object> m = new LinkedHashMap<>(6);
            m.put("type", "teamApply");
            m.put("title", "组队申请待处理");
            m.put("content", userFacade.nicknameOf(a.getUserId())
                    + " 申请加入「" + nameMap.getOrDefault(a.getTeamId(), "队伍") + "」");
            m.put("linkType", Enums.TargetType.TEAM);
            m.put("linkId", a.getTeamId());
            m.put("createdAt", a.getCreatedAt());
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> ruleUpdates(Long userId) {
        Page<NotifyMessage> page = new Page<>(1, MAX_ITEMS);
        notifyMapper.selectPage(page, new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, NotifyMessage.UNREAD)
                .in(NotifyMessage::getBizType, Enums.NotifyBiz.SYSTEM, Enums.NotifyBiz.PLATFORM)
                .orderByDesc(NotifyMessage::getId));
        List<Map<String, Object>> list = new ArrayList<>(page.getRecords().size());
        for (NotifyMessage m : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("type", "notice");
            item.put("title", m.getTitle());
            item.put("content", m.getContent());
            item.put("linkType", m.getLinkType());
            item.put("linkId", m.getLinkId());
            item.put("createdAt", m.getCreatedAt());
            list.add(item);
        }
        return list;
    }

    private long unreadOf(Long userId) {
        Long cnt = notifyMapper.selectCount(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, NotifyMessage.UNREAD));
        return cnt == null ? 0L : cnt;
    }

    /** 分页常量占位，保持与其他 Service 一致的规范化入口 */
    @SuppressWarnings("unused")
    private int normSize(Integer size) {
        return PageUtil.normalizeSize(size);
    }
}
