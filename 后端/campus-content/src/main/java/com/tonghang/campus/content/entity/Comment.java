package com.tonghang.campus.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 评论（《12-数据库设计文档》§2.2 comment）。
 */
@TableName("comment")
public class Comment {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DELETED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    /** 父评论（楼中楼） */
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private Integer likeCnt;
    /** 0 待审 1 正常 2 已删除 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getReplyToUserId() { return replyToUserId; }
    public void setReplyToUserId(Long replyToUserId) { this.replyToUserId = replyToUserId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getLikeCnt() { return likeCnt; }
    public void setLikeCnt(Integer likeCnt) { this.likeCnt = likeCnt; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
