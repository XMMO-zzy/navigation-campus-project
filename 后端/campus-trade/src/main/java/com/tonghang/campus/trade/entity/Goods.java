package com.tonghang.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 二手商品（《12-数据库设计文档》§2.4 goods）。
 */
@TableName("goods")
public class Goods {

    /** 状态：待审 */
    public static final int STATUS_PENDING = 0;
    /** 状态：在售 */
    public static final int STATUS_ON_SALE = 1;
    /** 状态：已下架 */
    public static final int STATUS_OFF_SALE = 2;
    /** 状态：已售出 */
    public static final int STATUS_SOLD = 3;
    /** 状态：已删除 */
    public static final int STATUS_DELETED = 4;
    /** 状态：审核退回 */
    public static final int STATUS_RETURNED = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卖家 */
    private Long userId;
    private String title;
    private Long categoryId;
    /** 现价 */
    private BigDecimal price;
    /** 原价 */
    private BigDecimal origPrice;
    /** 成色 1~5 */
    private Integer cond;
    private String dims;
    private String description;
    /** JSON：六面展示图 file_object.id / URL */
    private String images;
    private String tradePlace;
    /** 联系方式（下单后可见） */
    private String contact;
    /** 0 待审 1 在售 2 已下架 3 已售出 4 已删除 5 审核退回 */
    private Integer status;
    private Integer viewCnt;
    private Integer favoriteCnt;

    @TableLogic(value = "NULL", delval = "NOW()")
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getOrigPrice() { return origPrice; }
    public void setOrigPrice(BigDecimal origPrice) { this.origPrice = origPrice; }
    public Integer getCond() { return cond; }
    public void setCond(Integer cond) { this.cond = cond; }
    public String getDims() { return dims; }
    public void setDims(String dims) { this.dims = dims; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public String getTradePlace() { return tradePlace; }
    public void setTradePlace(String tradePlace) { this.tradePlace = tradePlace; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getViewCnt() { return viewCnt; }
    public void setViewCnt(Integer viewCnt) { this.viewCnt = viewCnt; }
    public Integer getFavoriteCnt() { return favoriteCnt; }
    public void setFavoriteCnt(Integer favoriteCnt) { this.favoriteCnt = favoriteCnt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
