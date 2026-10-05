package com.tonghang.campus.message.service;

import com.tonghang.campus.common.team.TeamQueryFacade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.tonghang.campus.message.entity.Team;
import com.tonghang.campus.message.entity.TeamMember;
import com.tonghang.campus.message.mapper.TeamMapper;
import com.tonghang.campus.message.mapper.TeamMemberMapper;
import org.springframework.stereotype.Service;

/**
 * 队伍只读查询门面实现（M3 提供给 M5 竞赛团队报名使用）。
 *
 * <p>来源：《13-API接口文档》§3.5「POST /competitions/{id}/enroll 报名竞赛（可关联 team_id）」；
 * 队伍归属 M3（《11》§2.2），故由 M3 实现 {@link TeamQueryFacade}，
 * 供 M5 在不直接操作 team / team_member 表的前提下完成校验。
 *
 * <p>依赖方向：campus-message → campus-event（仅接口，无循环）。
 */
@Service
public class TeamQueryFacadeImpl implements TeamQueryFacade {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;

    public TeamQueryFacadeImpl(TeamMapper teamMapper, TeamMemberMapper memberMapper) {
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public boolean exists(Long teamId) {
        if (teamId == null) {
            return false;
        }
        Team t = teamMapper.selectById(teamId);
        return t != null && (t.getStatus() == null || t.getStatus() != Team.STATUS_CLOSED);
    }

    @Override
    public boolean isMember(Long teamId, Long userId) {
        if (teamId == null || userId == null) {
            return false;
        }
        Long cnt = memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
        return cnt != null && cnt > 0;
    }

    @Override
    public String teamName(Long teamId) {
        if (teamId == null) {
            return null;
        }
        Team t = teamMapper.selectById(teamId);
        return t == null ? null : t.getName();
    }
}
