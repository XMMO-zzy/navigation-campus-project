package com.tonghang.campus.content.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.entity.OfficialNotice;
import com.tonghang.campus.content.mapper.OfficialNoticeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 官方通知接口（M2）。
 *
 * <p>来源：《13-API接口文档》§3.2：GET /notices（官方通知/规则更新/平台公告列表）— FR-M2-09。
 */
@Tag(name = "M2 官方通知")
@RestController
@RequestMapping("/notices")
public class NoticeController {

    private final OfficialNoticeMapper noticeMapper;

    public NoticeController(OfficialNoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /** GET /notices?type= —— 通知列表（FR-M2-09） */
    @Operation(summary = "官方通知/规则更新/平台公告列表")
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<OfficialNotice> w = new LambdaQueryWrapper<OfficialNotice>()
                .eq(OfficialNotice::getStatus, 1)
                .orderByDesc(OfficialNotice::getPublishAt)
                .orderByDesc(OfficialNotice::getId);
        if (type != null) {
            w.eq(OfficialNotice::getType, type);
        }

        Page<OfficialNotice> pageObj = new Page<>(p, s);
        noticeMapper.selectPage(pageObj, w);

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (OfficialNotice n : pageObj.getRecords()) {
            list.add(toMap(n, false));
        }
        return Result.ok(PageResult.of(list, pageObj.getTotal(), p, s));
    }

    /** GET /notices/{id} —— 通知详情 */
    @Operation(summary = "通知详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        OfficialNotice n = noticeMapper.selectById(id);
        if (n == null || n.getStatus() == null || n.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "通知不存在或已下线");
        }
        return Result.ok(toMap(n, true));
    }

    private Map<String, Object> toMap(OfficialNotice n, boolean withContent) {
        Map<String, Object> m = new LinkedHashMap<>(8);
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("typeText", typeText(n.getType()));
        m.put("source", n.getSource());
        m.put("title", n.getTitle());
        m.put("publishAt", n.getPublishAt());
        if (withContent) {
            m.put("content", n.getContent());
            m.put("linkUrl", n.getLinkUrl());
        } else if (n.getContent() != null) {
            m.put("summary", n.getContent().length() > 60
                    ? n.getContent().substring(0, 60) + "…" : n.getContent());
        }
        return m;
    }

    private String typeText(Integer type) {
        if (type == null) {
            return "通知";
        }
        switch (type) {
            case OfficialNotice.TYPE_RULE:
                return "规则更新";
            case OfficialNotice.TYPE_ANNOUNCE:
                return "平台公告";
            default:
                return "官方通知";
        }
    }
}
