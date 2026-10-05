package com.tonghang.campus.common.notify;

import com.tonghang.campus.common.constant.Enums;

import java.util.List;

/**
 * 站内通知写入能力（跨模块复用）。
 *
 * <p>来源：《10-需求文档》FR-M3-01 通知中心；《12》§2.3 notify_message。
 * <p>实现由 campus-message 模块提供，写入 campus-message 的 notify_message 表；
 * 交易 / 活动 / 搭子等模块通过本接口发通知，避免跨模块直接操作他域 Mapper（《11》§2.1）。
 */
public interface NotifySender {

    /**
     * 发一条站内通知。
     *
     * @param userId    接收者
     * @param bizType   业务类型，见 {@link Enums.NotifyBiz}
     * @param title     标题（≤64）
     * @param content   正文（≤500，可空）
     * @param linkType  跳转目标类型，见 {@link Enums.TargetType}（可空）
     * @param linkId    跳转目标 id（可空）
     * @param dedupKey  去重键（user+biz+date），为空则不去重
     */
    void send(Long userId, int bizType, String title, String content,
              Integer linkType, Long linkId, String dedupKey);

    /** 批量发送（同 biz/title/内容模板，仅接收者不同） */
    void sendBatch(List<Long> userIds, int bizType, String title, String content,
                   Integer linkType, Long linkId, String dedupKeyPrefix);
}
