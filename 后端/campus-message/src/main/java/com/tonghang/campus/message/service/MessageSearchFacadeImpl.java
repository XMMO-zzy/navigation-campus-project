package com.tonghang.campus.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.common.search.MessageSearchFacade;
import com.tonghang.campus.message.entity.NotifyMessage;
import com.tonghang.campus.message.mapper.NotifyMessageMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内消息检索门面实现（M3 提供给 M2 全局搜索使用）。
 *
 * <p>来源：《13》§3.2 全局搜索的「重要消息」类目 / 《10》FR-M2-07。
 *
 * <p>依赖方向：campus-message → campus-common（仅接口），无循环（《11》§2.1）。
 * 只检索**该用户自己的**消息，不做跨用户泄露。
 */
@Service
public class MessageSearchFacadeImpl implements MessageSearchFacade {

    /** 单次检索返回上限（搜索结果按类型拼接，避免压垮首屏） */
    private static final int MAX_RESULT = 50;

    /** 消息类搜索结果的内容类型（与前端 constants.CONTENT_TYPE 的扩展位约定为 10） */
    private static final int CONTENT_TYPE_MESSAGE = 10;

    private final NotifyMessageMapper mapper;

    public MessageSearchFacadeImpl(NotifyMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Map<String, Object>> searchMessages(Long userId, String keyword) {
        if (userId == null || keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String kw = keyword.trim();

        List<NotifyMessage> list = mapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .and(w -> w.like(NotifyMessage::getTitle, kw)
                        .or().like(NotifyMessage::getContent, kw))
                .orderByDesc(NotifyMessage::getCreatedAt)
                .last("LIMIT " + MAX_RESULT));

        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (NotifyMessage m : list) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("contentType", CONTENT_TYPE_MESSAGE);
            item.put("contentId", m.getId());
            item.put("title", m.getTitle());
            item.put("text", m.getContent());
            item.put("bizType", m.getBizType());
            item.put("isRead", m.getIsRead());
            item.put("linkType", m.getLinkType());
            item.put("linkId", m.getLinkId());
            item.put("createdAt", m.getCreatedAt());
            out.add(item);
        }
        return out;
    }
}
