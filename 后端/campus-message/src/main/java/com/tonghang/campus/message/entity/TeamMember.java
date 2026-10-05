package com.tonghang.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 队伍成员（《12-数据库设计文档》§2.3 team_member）。
 */
@TableName("team_member")
public class TeamMember {

    /** 角色：队长 */
    public static final int ROLE_LEADER = 0;
    /** 角色：成员 */
    public static final int ROLE_MEMBER = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    private Long userId;
    /** 0 队长 1 成员 */
    private Integer role;
    private String duty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getRole() { return role; }
    public void setRole(Integer role) { this.role = role; }
    public String getDuty() { return duty; }
    public void setDuty(String duty) { this.duty = duty; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
