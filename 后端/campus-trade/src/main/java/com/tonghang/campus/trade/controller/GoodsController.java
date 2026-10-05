package com.tonghang.campus.trade.controller;

import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.common.util.IdempotentService;
import com.tonghang.campus.trade.service.GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 二手商品接口（M4）。
 *
 * <p>来源：《13-API接口文档》§3.4：POST /goods、GET /goods、GET /goods/my、
 * GET /goods/{id}、PUT|DELETE /goods/{id}、POST /goods-wanted。
 */
@Tag(name = "M4 二手商品")
@RestController
public class GoodsController {

    private final GoodsService goodsService;
    private final IdempotentService idempotent;

    public GoodsController(GoodsService goodsService, IdempotentService idempotent) {
        this.goodsService = goodsService;
        this.idempotent = idempotent;
    }

    /** POST /goods —— 发布商品（FR-M4-01） */
    @Operation(summary = "发布商品（3 步向导数据一次提交；含图片审核结果）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/goods")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = UserContext.requireLogin();
        idempotent.checkAndLock(body == null ? null : (String) body.get("clientId"));
        return Result.ok(goodsService.create(uid, body));
    }

    /** GET /goods?category=&cond=&min=&max= —— 商品列表（FR-M4-02） */
    @Operation(summary = "商品列表（两栏瀑布流 + 筛选）")
    @GetMapping("/goods")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "category", required = false) Long category,
            @RequestParam(value = "cond", required = false) Integer cond,
            @RequestParam(value = "min", required = false) String min,
            @RequestParam(value = "max", required = false) String max,
            @RequestParam(value = "kw", required = false) String kw,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        Map<String, Object> params = new java.util.LinkedHashMap<>(5);
        params.put("category", category);
        params.put("cond", cond);
        params.put("min", min);
        params.put("max", max);
        params.put("kw", kw);
        return Result.ok(goodsService.list(UserContext.userIdOrNull(), params, page, size));
    }

    /** GET /goods/my?view= —— 我的视图（FR-M4-03） */
    @Operation(summary = "我的商品视图（view=published/favorite/orders）")
    @RequireRole(RequireRole.USER)
    @GetMapping("/goods/my")
    public Result<PageResult<Map<String, Object>>> my(
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(goodsService.my(UserContext.requireLogin(), view, page, size));
    }

    /** GET /goods/{id} —— 商品详情（FR-M4-02） */
    @Operation(summary = "商品详情（联系方式按权限脱敏）")
    @GetMapping("/goods/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(goodsService.detail(UserContext.userIdOrNull(), id));
    }

    /** PUT /goods/{id} —— 编辑商品（仅卖家） */
    @Operation(summary = "编辑商品（仅卖家）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/goods/{id}")
    public Result<Map<String, Object>> update(@PathVariable("id") Long id,
                                              @RequestBody Map<String, Object> body) {
        return Result.ok(goodsService.update(UserContext.requireLogin(), id, body));
    }

    /** DELETE /goods/{id} —— 下架 / 删除商品（仅卖家） */
    @Operation(summary = "下架 / 删除商品（仅卖家，软删）")
    @RequireRole(RequireRole.USER)
    @DeleteMapping("/goods/{id}")
    public Result<Void> remove(@PathVariable("id") Long id) {
        goodsService.remove(UserContext.requireLogin(), id);
        return Result.ok();
    }

    /** POST /goods-wanted —— 发布求购（FR-M4-06） */
    @Operation(summary = "发布求购（触发反向推送）")
    @RequireRole(RequireRole.USER)
    @PostMapping("/goods-wanted")
    public Result<Map<String, Object>> createWanted(@RequestBody Map<String, Object> body) {
        return Result.ok(goodsService.createWanted(UserContext.requireLogin(), body));
    }
}
