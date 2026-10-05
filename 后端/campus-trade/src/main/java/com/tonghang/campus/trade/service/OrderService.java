package com.tonghang.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.notify.NotifySender;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.trade.entity.CreditLog;
import com.tonghang.campus.trade.entity.Goods;
import com.tonghang.campus.trade.entity.GoodsOrder;
import com.tonghang.campus.trade.mapper.CreditLogMapper;
import com.tonghang.campus.trade.mapper.GoodsMapper;
import com.tonghang.campus.trade.mapper.GoodsOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 二手订单服务（M4）。
 *
 * <p>来源：《13-API接口文档》§3.4 与《12》§2.4 goods_order：
 * <ul>
 *   <li>POST /orders 下单（买家视角创建，待卖家确认）— FR-M4-04</li>
 *   <li>POST /orders/{no}/seller-confirm 卖家确认（同意交易）— FR-M4-04</li>
 *   <li>POST /orders/{no}/face-confirm <b>面交双向确认</b>（买卖双方各调一次）— FR-M4-04</li>
 *   <li>POST /orders/{no}/cancel 取消订单（状态机校验）— FR-M4-05</li>
 *   <li>GET /orders/{no} 订单详情 + 状态时间线 — FR-M4-04</li>
 *   <li>POST /orders/{no}/review 互评 + 信用分变更（P2）— FR-M4-07</li>
 * </ul>
 *
 * <p><b>状态机（阶段二决策之②：保留 20 待付款态）</b>：
 * <pre>
 *   10 待卖家确认 ──seller-confirm──▶ 20 待付款（30 分钟超时 → 70 超时关闭）
 *        │                                    │ 买家付款（骨架阶段无支付通道，直接入 30）
 *        │                                    ▼
 *        │                              30 待面交
 *        │                                    │ 双方线下见面
 *        │                                    ▼
 *        │                              40 待双方确认
 *        │                                    │ 买卖双方各调 face-confirm
 *        │                                    ▼
 *        │                              50 已完成
 *        └────────cancel（10/20）────────▶ 60 已取消
 * </pre>
 *
 * <p>注：《13》§3.4 未单列「付款」接口（支付通道由 P1 接入），
 * 故 seller-confirm 后订单进入 20 待付款；待支付通道接入后由支付回调推至 30。
 * 为让 P0 面交全链路可跑通，{@link #sellerConfirm} 在无支付通道时直接推进至 30，
 * 并通过返回体的 {@code payChannelReady=false} 标注该降级行为。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GoodsOrderMapper orderMapper;
    private final GoodsMapper goodsMapper;
    private final CreditLogMapper creditLogMapper;
    private final UserFacade userFacade;
    private final NotifySender notifySender;

    /** 待付款超时分钟数（《13》§1 / application.yml campus.order.pay-timeout-minutes） */
    private final int payTimeoutMinutes;
    /** 面交确认截止小时数（campus.order.confirm-deadline-hours） */
    private final int confirmDeadlineHours;
    /** 支付通道是否就绪（false 时 seller-confirm 直接推进到 待面交） */
    private final boolean payChannelReady;

    public OrderService(GoodsOrderMapper orderMapper,
                        GoodsMapper goodsMapper,
                        CreditLogMapper creditLogMapper,
                        UserFacade userFacade,
                        NotifySender notifySender,
                        @Value("${campus.order.pay-timeout-minutes:30}") int payTimeoutMinutes,
                        @Value("${campus.order.confirm-deadline-hours:72}") int confirmDeadlineHours,
                        @Value("${campus.order.pay-channel-ready:false}") boolean payChannelReady) {
        this.orderMapper = orderMapper;
        this.goodsMapper = goodsMapper;
        this.creditLogMapper = creditLogMapper;
        this.userFacade = userFacade;
        this.notifySender = notifySender;
        this.payTimeoutMinutes = payTimeoutMinutes;
        this.confirmDeadlineHours = confirmDeadlineHours;
        this.payChannelReady = payChannelReady;
    }

    /* ==================== 下单（FR-M4-04） ==================== */

    /**
     * 下单（POST /orders）—— 买家视角创建，状态 10 待卖家确认。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long buyerId, Long goodsId) {
        if (goodsId == null) {
            throw BizException.param("缺少 goodsId");
        }
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw BizException.notFound("商品不存在或已下架");
        }
        if (goods.getStatus() == null || goods.getStatus() != Goods.STATUS_ON_SALE) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "该商品当前不可下单");
        }
        if (goods.getUserId().equals(buyerId)) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "不能购买自己发布的商品");
        }

        // 同一买家对同一商品存在进行中的订单 → 拦截重复下单
        Long activeCnt = orderMapper.selectCount(new LambdaQueryWrapper<GoodsOrder>()
                .eq(GoodsOrder::getGoodsId, goodsId)
                .eq(GoodsOrder::getBuyerId, buyerId)
                .notIn(GoodsOrder::getStatus,
                        GoodsOrder.STATUS_CANCELED, GoodsOrder.STATUS_TIMEOUT,
                        GoodsOrder.STATUS_FINISHED));
        if (activeCnt != null && activeCnt > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "你已有一个进行中的订单，请勿重复下单");
        }

        GoodsOrder order = new GoodsOrder();
        order.setOrderNo(generateOrderNo());
        order.setGoodsId(goodsId);
        order.setBuyerId(buyerId);
        order.setSellerId(goods.getUserId());
        order.setAmount(goods.getPrice());
        order.setStatus(GoodsOrder.STATUS_WAIT_SELLER);
        order.setMeetPlace(goods.getTradePlace());
        order.setPayTimeoutAt(LocalDateTime.now().plusMinutes(payTimeoutMinutes));
        orderMapper.insert(order);

        notifySender.send(goods.getUserId(), Enums.NotifyBiz.TRADE,
                "有新的二手订单", "「" + goods.getTitle() + "」收到新的交易请求，请确认",
                Enums.TargetType.ORDER, order.getId(), "order-create:" + order.getOrderNo());

        return createResult(order);
    }

    /* ==================== 卖家确认（FR-M4-04） ==================== */

    /**
     * 卖家确认（POST /orders/{no}/seller-confirm）。
     *
     * <p>10 待卖家确认 → 20 待付款；若支付通道未就绪（P0 阶段），
     * 直接推进至 30 待面交，并在返回体标注 {@code payChannelReady=false}。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sellerConfirm(Long sellerId, String orderNo) {
        GoodsOrder order = requireOrder(orderNo);
        if (!order.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅卖家可确认交易");
        }
        if (order.getStatus() == null || order.getStatus() != GoodsOrder.STATUS_WAIT_SELLER) {
            throw new BizException(ErrorCode.STATUS_CONFLICT,
                    "当前状态（" + statusText(order.getStatus()) + "）不可确认");
        }

        int next = payChannelReady ? GoodsOrder.STATUS_WAIT_PAY : GoodsOrder.STATUS_WAIT_MEET;
        order.setStatus(next);
        order.setPayTimeoutAt(LocalDateTime.now().plusMinutes(payTimeoutMinutes));
        if (next == GoodsOrder.STATUS_WAIT_MEET) {
            order.setConfirmDeadline(LocalDateTime.now().plusHours(confirmDeadlineHours));
        }
        orderMapper.updateById(order);

        // 商品锁定：卖家确认后置为已售出占位（避免一物多单），取消时回滚
        markGoodsStatus(order.getGoodsId(), Goods.STATUS_OFF_SALE);

        notifySender.send(order.getBuyerId(), Enums.NotifyBiz.TRADE,
                "卖家已确认交易",
                payChannelReady ? "请在 " + payTimeoutMinutes + " 分钟内完成付款"
                        : "请与卖家约定面交时间地点",
                Enums.TargetType.ORDER, order.getId(), "order-seller-confirm:" + orderNo);

        Map<String, Object> data = createResult(order);
        data.put("payChannelReady", payChannelReady);
        return data;
    }

    /* ==================== 面交双向确认（FR-M4-04） ==================== */

    /**
     * 面交双向确认（POST /orders/{no}/face-confirm）。
     *
     * <p>买卖双方各调一次；先确认方返回 40 待双方确认，后确认方触发 50 已完成。
     * 同一用户重复确认 → 40006（与《13》§4 示例一致）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> faceConfirm(Long userId, String orderNo) {
        GoodsOrder order = requireOrder(orderNo);
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅交易双方可确认面交");
        }
        int st = order.getStatus() == null ? 0 : order.getStatus();
        if (st != GoodsOrder.STATUS_WAIT_MEET && st != GoodsOrder.STATUS_WAIT_CONFIRM) {
            throw new BizException(ErrorCode.STATUS_CONFLICT,
                    "当前状态（" + statusText(st) + "）不可确认面交");
        }

        LocalDateTime now = LocalDateTime.now();
        if (isBuyer) {
            if (order.getBuyerConfirmAt() != null) {
                throw new BizException(ErrorCode.DUPLICATE, "你已确认过面交，请等待对方确认");
            }
            order.setBuyerConfirmAt(now);
        } else {
            if (order.getSellerConfirmAt() != null) {
                throw new BizException(ErrorCode.DUPLICATE, "你已确认过面交，请等待对方确认");
            }
            order.setSellerConfirmAt(now);
        }

        boolean bothConfirmed = order.getBuyerConfirmAt() != null && order.getSellerConfirmAt() != null;
        if (bothConfirmed) {
            order.setStatus(GoodsOrder.STATUS_FINISHED);
            markGoodsStatus(order.getGoodsId(), Goods.STATUS_SOLD);
            applyCredit(order.getBuyerId(), Enums.CreditAction.ORDER_FINISH, 2, "完成二手交易");
            applyCredit(order.getSellerId(), Enums.CreditAction.ORDER_FINISH, 2, "完成二手交易");
        } else {
            order.setStatus(GoodsOrder.STATUS_WAIT_CONFIRM);
        }
        orderMapper.updateById(order);

        if (bothConfirmed) {
            Long counterId = isBuyer ? order.getSellerId() : order.getBuyerId();
            notifySender.send(counterId, Enums.NotifyBiz.TRADE,
                    "交易已完成", "订单 " + orderNo + " 双方已确认面交，交易完成",
                    Enums.TargetType.ORDER, order.getId(), "order-done:" + orderNo);
        }

        Map<String, Object> data = new LinkedHashMap<>(6);
        data.put("orderNo", order.getOrderNo());
        data.put("status", order.getStatus());
        data.put("statusText", order.getStatus() == GoodsOrder.STATUS_FINISHED ? "已完成" : "待对方确认");
        data.put("buyerConfirmAt", order.getBuyerConfirmAt());
        data.put("sellerConfirmAt", order.getSellerConfirmAt());
        return data;
    }

    /* ==================== 取消（FR-M4-05） ==================== */

    /**
     * 取消订单（POST /orders/{no}/cancel，状态机校验）。
     *
     * <p>允许取消的状态：10 待卖家确认 / 20 待付款 / 30 待面交 / 40 待双方确认一方未确认。
     * 已完成（50）不可取消。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(Long userId, String orderNo, String reason) {
        GoodsOrder order = requireOrder(orderNo);
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅交易双方可取消订单");
        }
        int st = order.getStatus() == null ? 0 : order.getStatus();
        if (st == GoodsOrder.STATUS_FINISHED) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "交易已完成，不可取消");
        }
        if (st == GoodsOrder.STATUS_CANCELED || st == GoodsOrder.STATUS_TIMEOUT) {
            throw new BizException(ErrorCode.DUPLICATE, "订单已关闭");
        }
        // 40 待双方确认阶段已有线下交易事实，禁止单方取消
        if (st == GoodsOrder.STATUS_WAIT_CONFIRM) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "已进入确认阶段，请联系对方协商");
        }

        order.setStatus(GoodsOrder.STATUS_CANCELED);
        order.setCancelReason(reason);
        orderMapper.updateById(order);

        // 回滚商品状态：下架占位 → 恢复在售
        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods != null && goods.getStatus() != null
                && (goods.getStatus() == Goods.STATUS_OFF_SALE || goods.getStatus() == Goods.STATUS_PENDING)) {
            markGoodsStatus(order.getGoodsId(), Goods.STATUS_ON_SALE);
        }

        // 违约取消扣信用分（卖家已确认后买家反悔 / 卖家反悔）
        if (st >= GoodsOrder.STATUS_WAIT_PAY) {
            applyCredit(userId, Enums.CreditAction.ORDER_CANCEL, -3, "取消已确认的订单");
        }

        Long counterId = isBuyer ? order.getSellerId() : order.getBuyerId();
        notifySender.send(counterId, Enums.NotifyBiz.TRADE,
                "订单已取消", "订单 " + orderNo + " 已被对方取消" + (reason == null ? "" : "：" + reason),
                Enums.TargetType.ORDER, order.getId(), "order-cancel:" + orderNo);

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("orderNo", order.getOrderNo());
        data.put("status", order.getStatus());
        data.put("statusText", statusText(order.getStatus()));
        return data;
    }

    /* ==================== 详情 / 列表 ==================== */

    /** 订单详情 + 状态时间线（GET /orders/{no}） */
    public Map<String, Object> detail(Long userId, String orderNo) {
        GoodsOrder order = requireOrder(orderNo);
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该订单");
        }
        return toDetail(order, userId, isBuyer);
    }

    /** 我的订单列表（GET /orders?role=buyer|seller&status=） */
    public PageResult<Map<String, Object>> list(Long userId, String role, Integer status,
                                                Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        boolean asSeller = "seller".equalsIgnoreCase(role);

        LambdaQueryWrapper<GoodsOrder> w = new LambdaQueryWrapper<GoodsOrder>()
                .eq(asSeller ? GoodsOrder::getSellerId : GoodsOrder::getBuyerId, userId)
                .orderByDesc(GoodsOrder::getId);
        if (status != null) {
            w.eq(GoodsOrder::getStatus, status);
        }
        Page<GoodsOrder> pageObj = new Page<>(p, s);
        orderMapper.selectPage(pageObj, w);

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (GoodsOrder o : pageObj.getRecords()) {
            list.add(toListItem(o, userId, !asSeller));
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /* ==================== 互评（P2 / FR-M4-07） ==================== */

    /**
     * 互评 + 信用分变更（POST /orders/{no}/review，P2）。
     *
     * <p>口径：评分 1~5；仅已完成订单可评；同一方只可评一次；
     * 低分（≤2）扣对方信用分，高分（≥4）加分（《10》FR-M4-07）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> review(Long userId, String orderNo, Map<String, Object> body) {
        GoodsOrder order = requireOrder(orderNo);
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅交易双方可评价");
        }
        if (order.getStatus() == null || order.getStatus() != GoodsOrder.STATUS_FINISHED) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "订单完成后才能评价");
        }
        if (body == null || body.get("score") == null) {
            throw BizException.param("请给出 1~5 的评分");
        }
        int score;
        try {
            score = Integer.parseInt(String.valueOf(body.get("score")));
        } catch (NumberFormatException e) {
            throw BizException.param("评分需为 1~5 的整数");
        }
        if (score < 1 || score > 5) {
            throw BizException.param("评分需为 1~5 的整数");
        }

        Long targetId;
        if (isBuyer) {
            if (order.getBuyerScore() != null) {
                throw new BizException(ErrorCode.DUPLICATE, "你已评价过该订单");
            }
            order.setBuyerScore(score);
            targetId = order.getSellerId();
        } else {
            if (order.getSellerScore() != null) {
                throw new BizException(ErrorCode.DUPLICATE, "你已评价过该订单");
            }
            order.setSellerScore(score);
            targetId = order.getBuyerId();
        }
        orderMapper.updateById(order);

        int delta = score >= 4 ? 2 : (score <= 2 ? -2 : 0);
        if (delta != 0) {
            applyCredit(targetId, Enums.CreditAction.REVIEW_SCORE, delta, "交易互评得分 " + score);
        }
        if (body.get("content") != null) {
            notifySender.send(targetId, Enums.NotifyBiz.TRADE,
                    "收到交易评价", "订单 " + orderNo + " 对方给出 " + score + " 分评价",
                    Enums.TargetType.ORDER, order.getId(), "order-review:" + orderNo + ":" + userId);
        }

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("orderNo", order.getOrderNo());
        data.put("buyerScore", order.getBuyerScore());
        data.put("sellerScore", order.getSellerScore());
        data.put("targetUserId", targetId);
        return data;
    }

    /* ==================== 定时任务：超时关闭（FR-M4-05） ==================== */

    /**
     * 关闭超时未付款订单（由定时任务调用，见 {@code OrderTimeoutJob}）。
     *
     * @return 关闭条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int closeTimeoutOrders() {
        List<GoodsOrder> list = orderMapper.selectList(new LambdaQueryWrapper<GoodsOrder>()
                .eq(GoodsOrder::getStatus, GoodsOrder.STATUS_WAIT_PAY)
                .lt(GoodsOrder::getPayTimeoutAt, LocalDateTime.now()));
        for (GoodsOrder o : list) {
            o.setStatus(GoodsOrder.STATUS_TIMEOUT);
            o.setCancelReason("超时未付款，系统自动关闭");
            orderMapper.updateById(o);
            markGoodsStatus(o.getGoodsId(), Goods.STATUS_ON_SALE);
            notifySender.send(o.getBuyerId(), Enums.NotifyBiz.TRADE,
                    "订单已超时关闭", "订单 " + o.getOrderNo() + " 超时未付款，已自动关闭",
                    Enums.TargetType.ORDER, o.getId(), "order-timeout:" + o.getOrderNo());
        }
        if (!list.isEmpty()) {
            log.info("[M4] 超时关闭待付款订单 {} 笔", list.size());
        }
        return list.size();
    }

    /* ==================== 内部工具 ==================== */

    private GoodsOrder requireOrder(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw BizException.param("缺少订单号");
        }
        GoodsOrder order = orderMapper.selectOne(new LambdaQueryWrapper<GoodsOrder>()
                .eq(GoodsOrder::getOrderNo, orderNo.trim())
                .last("LIMIT 1"));
        if (order == null) {
            throw BizException.notFound("订单不存在");
        }
        return order;
    }

    private void markGoodsStatus(Long goodsId, int status) {
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .set(Goods::getStatus, status));
    }

    /** 信用分变动（同时写 user.credit_score 与 credit_log） */
    private void applyCredit(Long userId, int action, int delta, String remark) {
        // user 表在 account 域，此处通过 credit_log 记录 + 通知；user.credit_score 由 account 域定时汇总
        CreditLog logRow = new CreditLog();
        logRow.setUserId(userId);
        logRow.setDelta(delta);
        logRow.setScore(null);
        logRow.setAction(action);
        logRow.setRemark(remark);
        creditLogMapper.insert(logRow);
    }

    private Map<String, Object> createResult(GoodsOrder order) {
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("orderNo", order.getOrderNo());
        data.put("status", order.getStatus());
        data.put("statusText", statusText(order.getStatus()));
        data.put("payTimeoutAt", order.getPayTimeoutAt());
        return data;
    }

    private Map<String, Object> toListItem(GoodsOrder o, Long userId, boolean asBuyer) {
        Map<String, Object> m = new LinkedHashMap<>(12);
        m.put("orderNo", o.getOrderNo());
        m.put("status", o.getStatus());
        m.put("statusText", statusText(o.getStatus()));
        m.put("amount", o.getAmount());
        m.put("role", asBuyer ? "buyer" : "seller");
        m.put("createdAt", o.getCreatedAt());

        Goods g = goodsMapper.selectById(o.getGoodsId());
        if (g != null) {
            Map<String, Object> goods = new LinkedHashMap<>(5);
            goods.put("id", g.getId());
            goods.put("title", g.getTitle());
            goods.put("price", g.getPrice());
            goods.put("image", firstImageOf(g.getImages()));
            m.put("goods", goods);
        }

        Long counterId = asBuyer ? o.getSellerId() : o.getBuyerId();
        UserFacade.UserBrief u = userFacade.get(counterId);
        Map<String, Object> counter = new LinkedHashMap<>(4);
        counter.put("id", counterId);
        counter.put("nickname", u == null ? "用户" + counterId : u.getNickname());
        counter.put("avatar", u == null ? null : u.getAvatar());
        m.put("counterpart", counter);
        return m;
    }

    private Map<String, Object> toDetail(GoodsOrder o, Long userId, boolean asBuyer) {
        Map<String, Object> m = toListItem(o, userId, asBuyer);
        m.put("meetPlace", o.getMeetPlace());
        m.put("cancelReason", o.getCancelReason());
        m.put("buyerConfirmAt", o.getBuyerConfirmAt());
        m.put("sellerConfirmAt", o.getSellerConfirmAt());
        m.put("confirmDeadline", o.getConfirmDeadline());
        m.put("buyerScore", o.getBuyerScore());
        m.put("sellerScore", o.getSellerScore());
        m.put("buyerConfirmed", o.getBuyerConfirmAt() != null);
        m.put("sellerConfirmed", o.getSellerConfirmAt() != null);
        m.put("canCancel", canCancel(o.getStatus()));
        m.put("canFaceConfirm", (o.getStatus() != null
                && (o.getStatus() == GoodsOrder.STATUS_WAIT_MEET
                || o.getStatus() == GoodsOrder.STATUS_WAIT_CONFIRM))
                && (asBuyer ? o.getBuyerConfirmAt() == null : o.getSellerConfirmAt() == null));
        m.put("timeline", timeline(o));
        return m;
    }

    /** 状态时间线（《13》§3.4「订单详情 + 状态时间线」） */
    private List<Map<String, Object>> timeline(GoodsOrder o) {
        List<Map<String, Object>> tl = new ArrayList<>();
        tl.add(node("下单", o.getCreatedAt(), true, null));
        tl.add(node("卖家确认", null, reached(o.getStatus(), GoodsOrder.STATUS_WAIT_PAY), null));
        tl.add(node("买家付款", null, reached(o.getStatus(), GoodsOrder.STATUS_WAIT_MEET), null));
        tl.add(node("待面交", null, reached(o.getStatus(), GoodsOrder.STATUS_WAIT_MEET),
                o.getMeetPlace()));
        if (o.getStatus() != null && o.getStatus() == GoodsOrder.STATUS_FINISHED) {
            tl.add(node("交易完成", null, true, null));
        } else if (o.getStatus() != null
                && (o.getStatus() == GoodsOrder.STATUS_CANCELED || o.getStatus() == GoodsOrder.STATUS_TIMEOUT)) {
            tl.add(node(o.getStatus() == GoodsOrder.STATUS_TIMEOUT ? "超时关闭" : "已取消",
                    null, true, o.getCancelReason()));
        }
        return tl;
    }

    private Map<String, Object> node(String title, LocalDateTime at, boolean done, String extra) {
        Map<String, Object> n = new LinkedHashMap<>(4);
        n.put("title", title);
        n.put("at", at);
        n.put("done", done);
        n.put("extra", extra);
        return n;
    }

    /** 状态是否已达到或超过目标状态（用于时间线推进标记） */
    private boolean reached(Integer status, int target) {
        if (status == null) {
            return false;
        }
        if (status == GoodsOrder.STATUS_CANCELED || status == GoodsOrder.STATUS_TIMEOUT) {
            return false;
        }
        return status >= target;
    }

    private boolean canCancel(Integer status) {
        if (status == null) {
            return false;
        }
        return status == GoodsOrder.STATUS_WAIT_SELLER
                || status == GoodsOrder.STATUS_WAIT_PAY
                || status == GoodsOrder.STATUS_WAIT_MEET;
    }

    private String firstImageOf(String imagesJson) {
        List<String> list = com.tonghang.campus.trade.support.JsonUtil.toStringList(imagesJson);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 单号生成：{@code G + yyyyMMdd + 4 位随机}，与《13》§4 示例
     * {@code G20261120A8E2} 的「G + 日期 + 业务码」结构一致。
     */
    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DATE_FMT);
        String rand = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000)).toUpperCase();
        while (rand.length() < 4) {
            rand = "0" + rand;
        }
        return "G" + date + rand;
    }

    /** 状态文案（与《13》§4 示例一致） */
    static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case GoodsOrder.STATUS_WAIT_SELLER:
                return "待卖家确认";
            case GoodsOrder.STATUS_WAIT_PAY:
                return "待付款";
            case GoodsOrder.STATUS_WAIT_MEET:
                return "待面交";
            case GoodsOrder.STATUS_WAIT_CONFIRM:
                return "待对方确认";
            case GoodsOrder.STATUS_FINISHED:
                return "已完成";
            case GoodsOrder.STATUS_CANCELED:
                return "已取消";
            case GoodsOrder.STATUS_TIMEOUT:
                return "超时关闭";
            default:
                return "未知";
        }
    }
}
