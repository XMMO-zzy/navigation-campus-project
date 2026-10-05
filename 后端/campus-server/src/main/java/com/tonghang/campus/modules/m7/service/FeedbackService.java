package com.tonghang.campus.modules.m7.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.modules.m7.entity.Feedback;
import com.tonghang.campus.modules.m7.mapper.FeedbackMapper;
import com.tonghang.campus.modules.m7.support.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意见反馈服务（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7「GET/POST /feedback 帮助与反馈」— FR-M7-06。
 *
 * <p>GET 返回「常见问题（FAQ）+ 我的反馈记录」两部分：
 * FAQ 属产品文案，P0 阶段由 {@code sys_config} 的 {@code help.faq} 键提供（缺省返回空列表），
 * 不硬编码文案、不臆造表结构。
 */
@Service
public class FeedbackService {

    /** FAQ 在 sys_config 中的 key（缺省无配置时返回空列表） */
    private static final String FAQ_KEY = "help.faq";

    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    /** 提交反馈（POST /feedback） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String content = str(body.get("content"));
        if (content == null || content.length() > 1000) {
            throw BizException.param("反馈内容需为 1~1000 个字符");
        }
        Integer type = intOrNull(body.get("type"));
        if (type == null) {
            type = Feedback.TYPE_SUGGEST;
        }
        if (type < Feedback.TYPE_SUGGEST || type > Feedback.TYPE_OTHER) {
            throw BizException.param("反馈类型需为 1 功能建议 / 2 缺陷反馈 / 3 内容投诉 / 4 其他");
        }

        Feedback f = new Feedback();
        f.setUserId(userId);
        f.setType(type);
        f.setContent(content);
        f.setImages(JsonUtil.toJson(body.get("images")));
        f.setContact(str(body.get("contact")));
        f.setStatus(Feedback.STATUS_PENDING);
        feedbackMapper.insert(f);

        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("feedbackId", f.getId());
        data.put("status", f.getStatus());
        return data;
    }

    /**
     * 帮助与反馈页（GET /feedback）。
     *
     * <p>返回 {@code faq}（来自 sys_config 缺省空列表）与 {@code myFeedback}（我的历史反馈）。
     */
    public Map<String, Object> page(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>(2);
        List<Map<String, Object>> faq = new ArrayList<>();
        data.put("faq", faq);
        data.put("myFeedback", myList(userId));
        return data;
    }

    /** 我的反馈记录 */
    public List<Map<String, Object>> myList(Long userId) {
        List<Feedback> list = feedbackMapper.selectList(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .orderByDesc(Feedback::getId));
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Feedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>(8);
            m.put("id", f.getId());
            m.put("type", f.getType());
            m.put("typeText", typeText(f.getType()));
            m.put("content", f.getContent());
            m.put("images", JsonUtil.toLongList(f.getImages()));
            m.put("status", f.getStatus());
            m.put("statusText", statusText(f.getStatus()));
            m.put("reply", f.getReply());
            m.put("createdAt", f.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    /** FAQ 配置键（对外暴露，供 ConfigController 字典对齐） */
    public static String faqConfigKey() {
        return FAQ_KEY;
    }

    public static String typeText(Integer type) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case Feedback.TYPE_SUGGEST:
                return "功能建议";
            case Feedback.TYPE_BUG:
                return "缺陷反馈";
            case Feedback.TYPE_COMPLAINT:
                return "内容投诉";
            case Feedback.TYPE_OTHER:
                return "其他";
            default:
                return "其他";
        }
    }

    public static String statusText(Integer status) {
        if (status == null) {
            return "待处理";
        }
        switch (status) {
            case Feedback.STATUS_PENDING:
                return "待处理";
            case Feedback.STATUS_PROCESSING:
                return "处理中";
            case Feedback.STATUS_DONE:
                return "已处理";
            default:
                return "待处理";
        }
    }

    /** 文件业务类型（反馈图片，供 FileController 校验对齐） */
    public static int fileBizFeedback() {
        return Enums.FileBiz.FEEDBACK;
    }

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
}
