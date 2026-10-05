package com.tonghang.campus.modules.m7.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.modules.m7.entity.SysConfig;
import com.tonghang.campus.modules.m7.mapper.SysConfigMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统字典接口（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7「GET /configs 前端字典（品类/成色/话题等 sys_config）」。
 * <p>说明：前端 framework/constants.js 已内置同值兜底，本接口用于运营期动态调整。
 */
@Tag(name = "M7 平台支撑 · 字典")
@RestController
@RequestMapping("/configs")
public class ConfigController {

    private final SysConfigMapper sysConfigMapper;

    public ConfigController(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    /** GET /configs —— 全部字典，key 去前缀返回 */
    @Operation(summary = "前端字典（品类/成色/搭子类型等）")
    @GetMapping
    public Result<Map<String, String>> all() {
        List<SysConfig> list = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId));
        Map<String, String> data = new LinkedHashMap<>();
        for (SysConfig c : list) {
            data.put(c.getCfgKey(), c.getCfgValue());
        }
        return Result.ok(data);
    }
}
