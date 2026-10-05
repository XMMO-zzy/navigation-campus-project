package com.tonghang.campus.modules.m7.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.modules.m7.entity.Report;
import com.tonghang.campus.modules.m7.mapper.ReportMapper;
import com.tonghang.campus.modules.m7.support.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报服务（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7：
 * <ul>
 *   <li>POST /reports 举报（原因 + 证据材料）— FR-M7-03</li>
 *   <li>GET /reports/my 我的举报记录（进度/结果/申诉入口）— FR-M7-06</li>
 * </ul>
 *
 * <p>《13》§4「◆ POST /reports」响应示例：{@code { reportId, status: 0, slaHours: 24 }}。
 * 处理动作（工单流转）属管理端 {@code /admin/**}，本服务只负责提交与查询。
 */
@Service
public class ReportService {

    /** SLA 小时数（《13》§4 示例固定 24） */
    public static final int SLA_HOURS = 24;

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    /**
     * 提交举报（FR-M7-03 / POST /reports）。
     *
     * <p>字段取自《13》§4 示例：targetType / targetId / reasonType / desc / evidenceFileIds。
     * <p>防刷：同一用户对同一目标 24h 内只允许一条待处理举报（重复 → 40006）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        Integer targetType = intOrNull(body.get("targetType"));
        Long targetId = longOrNull(body.get("targetId"));
        Integer reasonType = intOrNull(body.get("reasonType"));
        if (targetType == null || targetId == null) {
            throw BizException.param("请指定举报对象（targetType / targetId）");
        }
        if (reasonType == null || reasonType < Report.REASON_FAKE || reasonType > Report.REASON_OTHER) {
            throw BizException.param("举报原因需为 1 虚假 / 2 诈骗 / 3 侵权 / 4 违规 / 5 其他");
        }
        String desc = str(body.get("desc"));
        if (desc != null && desc.length() > 500) {
            throw BizException.param("补充说明不能超过 500 个字符");
        }

        // 幂等防刷：同一用户 + 同一目标，已有未处理工单则拒绝
        Long pending = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .in(Report::getStatus, Report.STATUS_PENDING, Report.STATUS_PROCESSING));
        if (pending != null && pending > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "你已举报过该内容，平台正在处理中");
        }

        Report r = new Report();
        r.setReporterId(userId);
        r.setTargetType(targetType);
        r.setTargetId(targetId);
        r.setReasonType(reasonType);
        r.setDesc(desc);
        r.setEvidence(JsonUtil.toJson(body.get("evidenceFileIds")));
        r.setStatus(Report.STATUS_PENDING);
        reportMapper.insert(r);

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("reportId", r.getId());
        data.put("status", r.getStatus());
        data.put("slaHours", SLA_HOURS);
        return data;
    }

    /** 我的举报记录（FR-M7-06 / GET /reports/my，含进度 / 结果 / 申诉入口标记） */
    public PageResult<Map<String, Object>> my(Long userId, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        Page<Report> pageObj = new Page<>(p, s);
        reportMapper.selectPage(pageObj, new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .orderByDesc(Report::getId));

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (Report r : pageObj.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>(12);
            m.put("id", r.getId());
            m.put("targetType", r.getTargetType());
            m.put("targetTypeText", targetTypeText(r.getTargetType()));
            m.put("targetId", r.getTargetId());
            m.put("reasonType", r.getReasonType());
            m.put("reasonTypeText", reasonTypeText(r.getReasonType()));
            m.put("desc", r.getDesc());
            m.put("evidence", JsonUtil.toLongList(r.getEvidence()));
            m.put("status", r.getStatus());
            m.put("statusText", statusText(r.getStatus()));
            m.put("result", r.getResult());
            m.put("handledAt", r.getHandledAt());
            m.put("createdAt", r.getCreatedAt());
            // 申诉入口：已处理 / 驳回的工单允许申诉（《10》FR-M7-06）
            m.put("canAppeal", r.getStatus() != null
                    && (r.getStatus() == Report.STATUS_DONE || r.getStatus() == Report.STATUS_REJECTED));
            list.add(m);
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /* ---------------- 文案工具 ---------------- */

    public static String statusText(Integer status) {
        if (status == null) {
            return "待处理";
        }
        switch (status) {
            case Report.STATUS_PENDING:
                return "待处理";
            case Report.STATUS_PROCESSING:
                return "处理中";
            case Report.STATUS_DONE:
                return "已处理";
            case Report.STATUS_REJECTED:
                return "已驳回";
            default:
                return "待处理";
        }
    }

    public static String reasonTypeText(Integer reason) {
        if (reason == null) {
            return null;
        }
        switch (reason) {
            case Report.REASON_FAKE:
                return "虚假信息";
            case Report.REASON_FRAUD:
                return "诈骗";
            case Report.REASON_INFRINGE:
                return "侵权";
            case Report.REASON_VIOLATE:
                return "违规";
            case Report.REASON_OTHER:
                return "其他";
            default:
                return "其他";
        }
    }

    /** 多态目标类型文案（《12》§1.3 通用多态字段） */
    public static String targetTypeText(Integer targetType) {
        if (targetType == null) {
            return null;
        }
        switch (targetType) {
            case Enums.TargetType.POST:
                return "动态";
            case Enums.TargetType.GOODS:
                return "商品";
            case Enums.TargetType.ACTIVITY:
                return "活动";
            case Enums.TargetType.COMPETITION:
                return "竞赛";
            case Enums.TargetType.BUDDY:
                return "搭子";
            case Enums.TargetType.COMMENT:
                return "评论";
            default:
                return "其他";
        }
    }

    /* ---------------- 入参工具 ---------------- */

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
