package com.tonghang.campus.common.notify;

import com.tonghang.campus.common.constant.Enums;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link NotifySender} 的降级实现。
 *
 * <p>message 模块未装配时（例如只跑 M1 冒烟）不阻断主链路，仅记录调试日志。
 */
@Component
public class NoopNotifySender implements NotifySender {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NoopNotifySender.class);

    private final ObjectProvider<NotifySender> delegate;

    public NoopNotifySender(ObjectProvider<NotifySender> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void send(Long userId, int bizType, String title, String content,
                     Integer linkType, Long linkId, String dedupKey) {
        NotifySender real = resolve();
        if (real != null) {
            real.send(userId, bizType, title, content, linkType, linkId, dedupKey);
            return;
        }
        log.debug("[notify-noop] uid={} biz={} title={}", userId, bizType, title);
    }

    @Override
    public void sendBatch(List<Long> userIds, int bizType, String title, String content,
                          Integer linkType, Long linkId, String dedupKeyPrefix) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long uid : userIds) {
            send(uid, bizType, title, content, linkType, linkId,
                    dedupKeyPrefix == null ? null : dedupKeyPrefix + ":" + uid);
        }
    }

    /** 排除自身，取真实实现 */
    private NotifySender resolve() {
        NotifySender real = delegate.getIfAvailable();
        return real == this ? null : real;
    }

    /** 便捷方法：交易类通知 */
    public static void trade(NotifySender sender, Long uid, String title, String content,
                             Long orderRefId, String dedup) {
        sender.send(uid, Enums.NotifyBiz.TRADE, title, content,
                Enums.TargetType.ORDER, orderRefId, dedup);
    }
}
