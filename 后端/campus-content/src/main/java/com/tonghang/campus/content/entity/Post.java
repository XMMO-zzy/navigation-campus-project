package com.tonghang.campus.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 动态（《12-数据库设计文档》§2.2 post）。
 */
@TableName("post")
public class Post {

    /** 动态类型：图文 */
    public static final int TYPE_IMAGE_TEXT = 1;
    /** 动态类型：经验分享 */
    public static final int TYPE_EXPERIENCE = 2;
    /** 动态类型：学习打卡 */
    public static final int TYPE_CHECK_IN = 3;
    /** 动态类型：组队需求 */
    public static final int TYPE_TEAM_NEED = 4;

    /** 审核状态：待审 */
    public static final int STATUS_PENDING = 0;
    /** 审核状态：已通过 */
    public static final int STATUS_PASSED = 1;
    /** 审核状态：已下架 */
    public static final int STATUS_OFFLINE = 2;
    /** 审核状态：审核退回 */
    public static final int STATUS_RETURNED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 1 图文 2 经验分享 3 学习打卡 4 组队需求 */
    private Integer postType;
    private String content;
    /** JSON：图片 file_object.id 列表 */
    private String images;
    /** 关联内容类型（多态，见 Enums.TargetType） */
    private Integer linkType;
    private Long linkId;
    /** 0 公开 1 仅关注 2 私密 */
    private Integer visibleScope;
    /** 0 所有人 1 关注的人 2 仅自己 3 关闭 */
    private Integer commentPermission;
    private Integer likeCnt;
    private Integer commentCnt;
    private Integer favoriteCnt;
    private Integer viewCnt;
    /** 0 待审 1 已通过 2 已下架 3 审核退回 */
    private Integer status;
    private Integer isTop;

    @TableLogic(value = "NULL", delval = "NOW()")
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /* ---------------- getter / setter ---------------- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getPostType() { return postType; }
    public void setPostType(Integer postType) { this.postType = postType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public Integer getLinkType() { return linkType; }
    public void setLinkType(Integer linkType) { this.linkType = linkType; }
    public Long getLinkId() { return linkId; }
    public void setLinkId(Long linkId) { this.linkId = linkId; }
    public Integer getVisibleScope() { return visibleScope; }
    public void setVisibleScope(Integer visibleScope) { this.visibleScope = visibleScope; }
    public Integer getCommentPermission() { return commentPermission; }
    public void setCommentPermission(Integer commentPermission) { this.commentPermission = commentPermission; }
    public Integer getLikeCnt() { return likeCnt; }
    public void setLikeCnt(Integer likeCnt) { this.likeCnt = likeCnt; }
    public Integer getCommentCnt() { return commentCnt; }
    public void setCommentCnt(Integer commentCnt) { this.commentCnt = commentCnt; }
    public Integer getFavoriteCnt() { return favoriteCnt; }
    public void setFavoriteCnt(Integer favoriteCnt) { this.favoriteCnt = favoriteCnt; }
    public Integer getViewCnt() { return viewCnt; }
    public void setViewCnt(Integer viewCnt) { this.viewCnt = viewCnt; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getIsTop() { return isTop; }
    public void setIsTop(Integer isTop) { this.isTop = isTop; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
