package com.tonghang.campus.trade.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.common.util.IdempotentService;
import com.tonghang.campus.trade.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 二手订单接口（M4）。
 *
 * <p>来源：《13-API接口文档》§3.4：
 * POST /orders、POST /orders/{no}/seller-confirm、POST /orders/{no}/face-confirm、
 * POST /orders/{no}/cancel、GET /orders/{no}、POST /orders/{no}/review。
 */
@Tag(name = "M4 二手订单")
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotentService idempotent;

    public OrderController(OrderService orderService, IdempotentService idempotent) {
        this.orderService = orderService;
        this.idempotent = idempotent;
    }

    /** POST /orders —— 下单（FR-M4-04） */
    @Operation(summary = "下单（买家视角创建，待卖家确认）")
    @RequireRole(RequireRole.USER)
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        Long goodsId = body == null || body.get("goodsId") == null
                ? null : Long.valueOf(String.valueOf(body.get("goodsId")));
        return Result.ok(orderService.create(uid, goodsId));
    }

    /** GET /orders?role=&status= —— 我的订单列表（FR-M4-03） */
    @Operation(summary = "我的订单列表（role=buyer/seller，status 可选）")
    @RequireRole(RequireRole.USER)
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(orderService.list(UserContext.requireLogin(), role, status, page, size));
    }

    /** POST /orders/{no}/seller-confirm —— 卖家确认（FR-M4-04） */
    @Operation(summary = "卖家确认（同意交易）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{no}/seller-confirm")
    public Result<Map<String, Object>> sellerConfirm(@PathVariable("no") String orderNo) {
        return Result.ok(orderService.sellerConfirm(UserContext.requireLogin(), orderNo));
    }

    /**
     * POST /orders/{no}/face-confirm —— 面交双向确认（FR-M4-04）。
     *
     * <p>买卖双方各调一次；同一用户重复确认 → 40006（与《13》§4 示例一致）。
     */
    @Operation(summary = "面交双向确认（买卖双方各调一次）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{no}/face-confirm")
    public Result<Map<String, Object>> faceConfirm(@PathVariable("no") String orderNo,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(orderService.faceConfirm(uid, orderNo));
    }

    /** POST /orders/{no}/cancel —— 取消订单（FR-M4-05） */
    @Operation(summary = "取消订单（状态机校验）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{no}/cancel")
    public Result<Map<String, Object>> cancel(@PathVariable("no") String orderNo,
                                              @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("cancelReason") == null
                ? null : String.valueOf(body.get("cancelReason"));
        return Result.ok(orderService.cancel(UserContext.requireLogin(), orderNo, reason));
    }

    /** GET /orders/{no} —— 订单详情 + 状态时间线（FR-M4-04） */
    @Operation(summary = "订单详情 + 状态时间线")
    @RequireRole(RequireRole.USER)
    @GetMapping("/{no}")
    public Result<Map<String, Object>> detail(@PathVariable("no") String orderNo) {
        return Result.ok(orderService.detail(UserContext.requireLogin(), orderNo));
    }

    /** POST /orders/{no}/review —— 互评（P2，FR-M4-07） */
    @Operation(summary = "互评 + 信用分变更（P2）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/{no}/review")
    public Result<Map<String, Object>> review(@PathVariable("no") String orderNo,
                                              @RequestBody Map<String, Object> body) {
        return Result.ok(orderService.review(UserContext.requireLogin(), orderNo, body));
    }
}
