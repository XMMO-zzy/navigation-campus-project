package com.tonghang.campus.modules.m7.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 举报工单（《12-数据库设计文档》§2.7 report）。
 *
 * <p>来源：《13-API接口文档》§3.7「POST /reports 举报（原因 + 证据材料）」— FR-M7-03；
 * 「GET /reports/my 我的举报记录（进度/结果/申诉入口）」— FR-M7-06。
 * <p>SLA：24h（《13》§4「◆ POST /reports」响应 {@code slaHours: 24}），
 * 统计口径为 {@code idx_status_time(status, created_at)}。
 */
@TableName("report")
public class Report {

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：处理中 */
    public static final int STATUS_PROCESSING = 1;
    /** 状态：已处理 */
    public static final int STATUS_DONE = 2;
    /** 状态：驳回 */
    public static final int STATUS_REJECTED = 3;

    /** 原因：虚假信息 */
    public static final int REASON_FAKE = 1;
    /** 原因：诈骗 */
    public static final int REASON_FRAUD = 2;
    /** 原因：侵权 */
    public static final int REASON_INFRINGE = 3;
    /** 原因：违规 */
    public static final int REASON_VIOLATE = 4;
    /** 原因：其他 */
    public static final int REASON_OTHER = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;
    /** 多态目标类型，见 Enums.TargetType */
    private Integer targetType;
    private Long targetId;
    /** 1 虚假 2 诈骗 3 侵权 4 违规 5 其他 */
    private Integer reasonType;
    private String desc;
    /** JSON：证据 file_object.id 列表 */
    private String evidence;
    /** 0 待处理 1 处理中 2 已处理 3 驳回 */
    private Integer status;
    private Long handlerId;
    private String result;
    /** 24h SLA 统计口径 */
    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public Integer getTargetType() { return targetType; }
    public void setTargetType(Integer targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Integer getReasonType() { return reasonType; }
    public void setReasonType(Integer reasonType) { this.reasonType = reasonType; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
