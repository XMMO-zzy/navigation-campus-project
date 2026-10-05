package com.tonghang.campus.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 官方通知（《12-数据库设计文档》§2.3 official_notice）。
 */
@TableName("official_notice")
public class OfficialNotice {

    /** 类型：官方通知 */
    public static final int TYPE_NOTICE = 1;
    /** 类型：规则更新 */
    public static final int TYPE_RULE = 2;
    /** 类型：平台公告 */
    public static final int TYPE_ANNOUNCE = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 1 官方通知 2 规则更新 3 平台公告 */
    private Integer type;
    private String source;
    private String title;
    private String content;
    private String linkUrl;
    private LocalDateTime publishAt;
    /** 0 草稿 1 已发布 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public LocalDateTime getPublishAt() { return publishAt; }
    public void setPublishAt(LocalDateTime publishAt) { this.publishAt = publishAt; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
