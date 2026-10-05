package com.tonghang.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 求购帖（《12-数据库设计文档》§2.4 goods_wanted）。
 */
@TableName("goods_wanted")
public class GoodsWanted {

    /** 状态：待审 */
    public static final int STATUS_PENDING = 0;
    /** 状态：有效 */
    public static final int STATUS_VALID = 1;
    /** 状态：已关闭 */
    public static final int STATUS_CLOSED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    /** JSON：关键词（反向推送匹配） */
    private String keywords;
    /** 描述 */
    private String desc;
    private String contact;
    /** 0 待审 1 有效 2 已关闭 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
