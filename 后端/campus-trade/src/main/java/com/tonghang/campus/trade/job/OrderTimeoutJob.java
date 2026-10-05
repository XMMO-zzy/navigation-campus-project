package com.tonghang.campus.trade.job;

import com.tonghang.campus.trade.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时关闭定时任务（M4 / FR-M4-05）。
 *
 * <p>来源：《10-需求文档》FR-M4-05「订单超时（30 分钟未付款）自动关闭」；
 * 《12》§2.4 goods_order.pay_timeout_at。
 *
 * <p>调度：每 5 分钟扫描一次待付款且已超时的订单，置为 70 超时关闭并回滚商品状态。
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);

    private final OrderService orderService;

    public OrderTimeoutJob(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 每 5 分钟执行（cron: 秒 分 时 日 月 周） */
    @Scheduled(cron = "0 */5 * * * ?")
    public void closeTimeoutOrders() {
        try {
            int closed = orderService.closeTimeoutOrders();
            if (closed > 0) {
                log.info("[M4] 定时任务：关闭超时订单 {} 笔", closed);
            }
        } catch (Exception e) {
            log.error("[M4] 关闭超时订单失败：{}", e.getMessage(), e);
        }
    }
}
