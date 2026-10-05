package com.tonghang.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 二手订单（《12-数据库设计文档》§2.4 goods_order）。
 *
 * <p>状态机（阶段二决策之②：保留 20 待付款态，以《12》/《13》/《15》为准）：
 * <pre>
 *   10 待卖家确认 → 20 待付款（30min 超时）→ 30 待面交
 *                 → 40 待双方确认（面交双向）→ 50 已完成
 *   分支：60 已取消 / 70 超时关闭
 * </pre>
 */
@TableName("goods_order")
public class GoodsOrder {

    /** 待卖家确认 */
    public static final int STATUS_WAIT_SELLER = 10;
    /** 待付款（30 分钟超时） */
    public static final int STATUS_WAIT_PAY = 20;
    /** 待面交 */
    public static final int STATUS_WAIT_MEET = 30;
    /** 待双方确认 */
    public static final int STATUS_WAIT_CONFIRM = 40;
    /** 已完成 */
    public static final int STATUS_FINISHED = 50;
    /** 已取消 */
    public static final int STATUS_CANCELED = 60;
    /** 超时关闭 */
    public static final int STATUS_TIMEOUT = 70;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 唯一单号 */
    private String orderNo;
    private Long goodsId;
    private Long buyerId;
    private Long sellerId;
    /** 成交价 */
    private BigDecimal amount;
    /** 10~70，见类注释状态机 */
    private Integer status;
    /** 买家面交确认时间 */
    private LocalDateTime buyerConfirmAt;
    /** 卖家面交确认时间 */
    private LocalDateTime sellerConfirmAt;
    /** 30min 付款超时 */
    private LocalDateTime payTimeoutAt;
    /** 72h 确认截止 */
    private LocalDateTime confirmDeadline;
    private String meetPlace;
    private String cancelReason;
    /** 买家评分（P2 互评） */
    private Integer buyerScore;
    /** 卖家评分（P2 互评） */
    private Integer sellerScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getBuyerConfirmAt() { return buyerConfirmAt; }
    public void setBuyerConfirmAt(LocalDateTime buyerConfirmAt) { this.buyerConfirmAt = buyerConfirmAt; }
    public LocalDateTime getSellerConfirmAt() { return sellerConfirmAt; }
    public void setSellerConfirmAt(LocalDateTime sellerConfirmAt) { this.sellerConfirmAt = sellerConfirmAt; }
    public LocalDateTime getPayTimeoutAt() { return payTimeoutAt; }
    public void setPayTimeoutAt(LocalDateTime payTimeoutAt) { this.payTimeoutAt = payTimeoutAt; }
    public LocalDateTime getConfirmDeadline() { return confirmDeadline; }
    public void setConfirmDeadline(LocalDateTime confirmDeadline) { this.confirmDeadline = confirmDeadline; }
    public String getMeetPlace() { return meetPlace; }
    public void setMeetPlace(String meetPlace) { this.meetPlace = meetPlace; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Integer getBuyerScore() { return buyerScore; }
    public void setBuyerScore(Integer buyerScore) { this.buyerScore = buyerScore; }
    public Integer getSellerScore() { return sellerScore; }
    public void setSellerScore(Integer sellerScore) { this.sellerScore = sellerScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
