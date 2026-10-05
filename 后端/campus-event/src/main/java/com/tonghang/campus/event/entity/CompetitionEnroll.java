package com.tonghang.campus.event.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 竞赛报名与成果（《12-数据库设计文档》§2.5 competition_enroll）。
 */
@TableName("competition_enroll")
public class CompetitionEnroll {

    /** 状态：待审核 */
    public static final int STATUS_PENDING = 0;
    /** 状态：通过 */
    public static final int STATUS_PASSED = 1;
    /** 状态：拒绝 */
    public static final int STATUS_REJECTED = 2;
    /** 状态：已取消 */
    public static final int STATUS_CANCELED = 3;

    /** 成果：无 */
    public static final int RESULT_NONE = 0;
    /** 成果：一等奖 */
    public static final int RESULT_FIRST = 1;
    /** 成果：二等奖 */
    public static final int RESULT_SECOND = 2;
    /** 成果：三等奖 */
    public static final int RESULT_THIRD = 3;
    /** 成果：优秀奖 */
    public static final int RESULT_EXCELLENT = 4;
    /** 成果：入围 */
    public static final int RESULT_FINALIST = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long competitionId;
    private Long userId;
    /** 队伍参赛 */
    private Long teamId;
    private String name;
    private String phone;
    /** 报名信息 */
    private String applyMsg;
    /** 0 待审核 1 通过 2 拒绝 3 已取消 */
    private Integer status;
    /** 0 无 1 一等奖 2 二等奖 3 三等奖 4 优秀奖 5 入围 */
    private Integer result;
    /** 获奖说明 */
    private String awardDesc;
    /** 作品介绍 */
    private String workIntro;
    /** 演示链接 */
    private String workLink;
    /** JSON：附件 file_object.id */
    private String workFiles;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompetitionId() { return competitionId; }
    public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getApplyMsg() { return applyMsg; }
    public void setApplyMsg(String applyMsg) { this.applyMsg = applyMsg; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getResult() { return result; }
    public void setResult(Integer result) { this.result = result; }
    public String getAwardDesc() { return awardDesc; }
    public void setAwardDesc(String awardDesc) { this.awardDesc = awardDesc; }
    public String getWorkIntro() { return workIntro; }
    public void setWorkIntro(String workIntro) { this.workIntro = workIntro; }
    public String getWorkLink() { return workLink; }
    public void setWorkLink(String workLink) { this.workLink = workLink; }
    public String getWorkFiles() { return workFiles; }
    public void setWorkFiles(String workFiles) { this.workFiles = workFiles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
