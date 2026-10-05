package com.tonghang.campus.message.service;

import com.tonghang.campus.common.notify.NotifySender;
import com.tonghang.campus.message.entity.NotifyMessage;
import com.tonghang.campus.message.mapper.NotifyMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 站内通知写入实现（M3）。
 *
 * <p>来源：
 * <ul>
 *   <li>《10-需求文档》FR-M3-01 通知中心、FR-M3-02 已读与设置；</li>
 *   <li>《12》§2.3 notify_message（含 dedup_key 唯一键，去重规则 user+biz+date）。</li>
 * </ul>
 *
 * <p>本类实现 {@link NotifySender}，供交易/活动/搭子等模块跨域发通知；
 * 各模块不得直接注入 {@link NotifyMessageMapper}（《11》§2.3）。
 *
 * <p><b>@Primary 说明（必读）</b>：{@code NotifySender} 同时存在本实现与
 * campus-common 的兜底实现 {@code NoopNotifySender}，若不加 @Primary，
 * Spring 会因「expected single matching bean but found 2」而启动失败。
 * 这里把**真实实现标为 @Primary**：
 * <ul>
 *   <li>业务模块直接注入 {@code NotifySender} → 拿到本实现；</li>
 *   <li>NoopNotifySender 内部用 {@code ObjectProvider.getIfAvailable()} → 因存在 @Primary 而取到本实现，
 *       于是转发给它；仅当本实现缺席（未装配 message 模块）时才退化为只打日志。</li>
 * </ul>
 */
@Service
@Primary
public class NotifySenderImpl implements NotifySender {

    private static final Logger log = LoggerFactory.getLogger(NotifySenderImpl.class);

    private final NotifyMessageMapper mapper;

    public NotifySenderImpl(NotifyMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void send(Long userId, int bizType, String title, String content,
                     Integer linkType, Long linkId, String dedupKey) {
        if (userId == null) {
            return;
        }
        NotifyMessage msg = new NotifyMessage();
        msg.setUserId(userId);
        msg.setBizType(bizType);
        msg.setTitle(truncate(title, 64));
        msg.setContent(truncate(content, 500));
        msg.setLinkType(linkType);
        msg.setLinkId(linkId);
        msg.setIsRead(NotifyMessage.UNREAD);
        msg.setDedupKey(buildDedupKey(userId, bizType, dedupKey));
        try {
            mapper.insert(msg);
        } catch (DuplicateKeyException e) {
            // uk_dedup：同日同类通知已存在，按幂等处理
            log.debug("[M3] 通知去重命中 uid={} biz={} dedup={}", userId, bizType, dedupKey);
        }
    }

    @Override
    public void sendBatch(List<Long> userIds, int bizType, String title, String content,
                          Integer linkType, Long linkId, String dedupKeyPrefix) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long uid : userIds) {
            send(uid, bizType, title, content, linkType, linkId, dedupKeyPrefix);
        }
    }

    /**
     * 去重键：{@code user:biz:prefix:date}。
     *
     * <p>《12》§2.3 定义为 user+biz+date；额外带上可选 prefix 以支持
     * 「同一订单多阶段通知」等场景（prefix 传订单号等业务标识）。
     */
    private String buildDedupKey(Long userId, int bizType, String prefix) {
        StringBuilder sb = new StringBuilder(48);
        sb.append(userId).append(':').append(bizType);
        if (prefix != null && !prefix.trim().isEmpty()) {
            sb.append(':').append(prefix.trim());
        }
        sb.append(':').append(LocalDate.now());
        return sb.length() > 64 ? sb.substring(0, 64) : sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
