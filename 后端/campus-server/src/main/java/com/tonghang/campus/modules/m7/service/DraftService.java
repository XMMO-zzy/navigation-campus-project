package com.tonghang.campus.modules.m7.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.modules.m7.entity.Draft;
import com.tonghang.campus.modules.m7.mapper.DraftMapper;
import com.tonghang.campus.modules.m7.support.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 草稿服务（M7）。
 *
 * <p>来源：《13-API接口文档》§3.7「GET/PUT /drafts/{type} 草稿读取/保存（五类，JSON payload）」
 * — FR-M2-10；《12》§2.7 draft 唯一键 {@code uk_draft(user_id, draft_type)}。
 *
 * <p>type 取值：1 动态 / 2 商品 / 3 活动 / 4 竞赛 / 5 搭子（见 {@link Draft} 常量）。
 */
@Service
public class DraftService {

    private final DraftMapper draftMapper;

    public DraftService(DraftMapper draftMapper) {
        this.draftMapper = draftMapper;
    }

    /**
     * 读取草稿（GET /drafts/{type}）。
     *
     * <p>无草稿时返回 {@code {type, payload: null, updatedAt: null}}，
     * 前端据此进入空白编辑态，不视为错误。
     */
    public Map<String, Object> get(Long userId, Integer type) {
        int t = requireType(type);
        List<Draft> list = draftMapper.selectList(new LambdaQueryWrapper<Draft>()
                .eq(Draft::getUserId, userId)
                .eq(Draft::getDraftType, t)
                .last("LIMIT 1"));
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("type", t);
        if (list.isEmpty()) {
            data.put("payload", null);
            data.put("updatedAt", null);
            return data;
        }
        Draft d = list.get(0);
        data.put("payload", JsonUtil.toMap(d.getPayload()));
        data.put("updatedAt", d.getUpdatedAt());
        return data;
    }

    /**
     * 保存草稿（PUT /drafts/{type}）。
     *
     * <p>按 {@code uk_draft(user_id, draft_type)} 做 upsert：已存在则更新 payload，
     * 不存在则插入；payload 为空对象时视为「清空草稿」但保留行。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(Long userId, Integer type, Object payload) {
        int t = requireType(type);
        List<Draft> list = draftMapper.selectList(new LambdaQueryWrapper<Draft>()
                .eq(Draft::getUserId, userId)
                .eq(Draft::getDraftType, t)
                .last("LIMIT 1"));

        Draft d;
        if (list.isEmpty()) {
            d = new Draft();
            d.setUserId(userId);
            d.setDraftType(t);
            d.setPayload(JsonUtil.toJson(payload));
            draftMapper.insert(d);
        } else {
            d = list.get(0);
            d.setPayload(JsonUtil.toJson(payload));
            draftMapper.updateById(d);
        }

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("type", t);
        data.put("saved", true);
        data.put("updatedAt", d.getUpdatedAt());
        return data;
    }

    /** 删除草稿（发布成功后清理；《13》未单列，作为 save 的补充能力保留） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> remove(Long userId, Integer type) {
        int t = requireType(type);
        int deleted = draftMapper.delete(new LambdaQueryWrapper<Draft>()
                .eq(Draft::getUserId, userId)
                .eq(Draft::getDraftType, t));
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("type", t);
        data.put("deleted", deleted);
        return data;
    }

    /** 类型校验（《12》§2.7 draft.draft_type 值域 1~5） */
    private int requireType(Integer type) {
        if (type == null || type < Draft.TYPE_POST || type > Draft.TYPE_BUDDY) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "draft type 需为 1 动态 / 2 商品 / 3 活动 / 4 竞赛 / 5 搭子");
        }
        return type;
    }
}
