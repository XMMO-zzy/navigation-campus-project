package com.tonghang.campus.modules.m7.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文件对象（《12-数据库设计文档》§2.7 file_object）。
 *
 * <p>来源：《13-API接口文档》§3.7「POST /files/upload 文件上传
 * （返回 file_id/url + 审核状态，≤10MB，白名单）」— FR-M7-04。
 */
@TableName("file_object")
public class FileObject {

    /** 审核状态：待审 */
    public static final int AUDIT_PENDING = 0;
    /** 审核状态：通过 */
    public static final int AUDIT_PASSED = 1;
    /** 审核状态：拦截 */
    public static final int AUDIT_REJECTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 1 头像 2 动态 3 商品 4 活动 5 竞赛 6 搭子 7 认证 8 举报 9 反馈 */
    private Integer bizType;
    private String url;
    /** COS 对象键 */
    private String objectKey;
    private Long size;
    private String mime;
    /** 0 待审 1 通过 2 拦截 */
    private Integer auditStatus;
    /** 审核失败原因 */
    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getBizType() { return bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Integer getAuditStatus() { return auditStatus; }
    public void setAuditStatus(Integer auditStatus) { this.auditStatus = auditStatus; }
    public String getAuditRemark() { return auditRemark; }
    public void setAuditRemark(String auditRemark) { this.auditRemark = auditRemark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
