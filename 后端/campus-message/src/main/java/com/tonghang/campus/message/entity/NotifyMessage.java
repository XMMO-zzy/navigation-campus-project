package com.tonghang.campus.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 站内通知（《12-数据库设计文档》§2.3 notify_message）。
 */
@TableName("notify_message")
public class NotifyMessage {

    /** 未读 */
    public static final int UNREAD = 0;
    /** 已读 */
    public static final int READ = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 10 交易 20 组队 30 互动 40 系统 50 活动竞赛 60 平台 */
    private Integer bizType;
    private String title;
    private String content;
    private Integer linkType;
    private Long linkId;
    /** 0 未读 1 已读 */
    private Integer isRead;
    private LocalDateTime readAt;
    /** 去重键 user+biz+date */
    private String dedupKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getBizType() { return bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getLinkType() { return linkType; }
    public void setLinkType(Integer linkType) { this.linkType = linkType; }
    public Long getLinkId() { return linkId; }
    public void setLinkId(Long linkId) { this.linkId = linkId; }
    public Integer getIsRead() { return isRead; }
    public void setIsRead(Integer isRead) { this.isRead = isRead; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
