package com.tonghang.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 组队申请与邀请（《12-数据库设计文档》§2.3 team_apply）。
 */
@TableName("team_apply")
public class TeamApply {

    /** 方向：我发出的申请 */
    public static final int DIRECTION_SENT = 1;
    /** 方向：我收到的邀请 */
    public static final int DIRECTION_INVITED = 2;

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已通过 */
    public static final int STATUS_PASSED = 1;
    /** 状态：已拒绝 */
    public static final int STATUS_REJECTED = 2;
    /** 状态：已撤回 */
    public static final int STATUS_WITHDRAWN = 3;
    /** 状态：补充询问 */
    public static final int STATUS_ASKING = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;
    /** 发起方；direction=2 时为被邀请人 */
    private Long userId;
    /** 1 我发出的申请 2 我收到的邀请 */
    private Integer direction;
    private String message;
    private String replyMsg;
    /** JSON：展示技能 */
    private String skills;
    /** 0 待处理 1 已通过 2 已拒绝 3 已撤回 4 补充询问 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getDirection() { return direction; }
    public void setDirection(Integer direction) { this.direction = direction; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getReplyMsg() { return replyMsg; }
    public void setReplyMsg(String replyMsg) { this.replyMsg = replyMsg; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
