package com.tonghang.campus.modules.m7.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 意见反馈（《12-数据库设计文档》§2.7 feedback）。
 *
 * <p>来源：《13-API接口文档》§3.7「GET/POST /feedback 帮助与反馈」— FR-M7-06。
 */
@TableName("feedback")
public class Feedback {

    /** 类型：功能建议 */
    public static final int TYPE_SUGGEST = 1;
    /** 类型：缺陷反馈 */
    public static final int TYPE_BUG = 2;
    /** 类型：内容投诉 */
    public static final int TYPE_COMPLAINT = 3;
    /** 类型：其他 */
    public static final int TYPE_OTHER = 4;

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 0;
    /** 状态：处理中 */
    public static final int STATUS_PROCESSING = 1;
    /** 状态：已处理 */
    public static final int STATUS_DONE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 1 功能建议 2 缺陷反馈 3 内容投诉 4 其他 */
    private Integer type;
    private String content;
    /** JSON：图片 file_object.id */
    private String images;
    private String contact;
    /** 0 待处理 1 处理中 2 已处理 */
    private Integer status;
    private String reply;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
