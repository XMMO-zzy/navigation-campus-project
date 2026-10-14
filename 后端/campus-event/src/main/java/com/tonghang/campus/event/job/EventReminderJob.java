package com.tonghang.campus.event.job;

import com.tonghang.campus.event.service.EventReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 活动与赛事提醒定时任务（M5 / FR-M3-05 / T2.5）。
 *
 * <p>调度：每小时扫描一次，覆盖
 * ① 报名开始 ② 即将截止（24h 内）③ 赛程提醒（初赛 24h 内）。
 *
 * <p>去重：通知统一带 dedupKey，{@code NotifySender} 按「用户+biz+date」唯一，
 * 因此每小时扫描不会造成重复打扰。
 *
 * <p>「规则变化」类提醒依赖官方通知（official_notice）的人工发布，
 * 由 M7 治理侧发布后在消息流中触达，不在本任务范围。
 *
 * <p>微信模板消息兜底（FR-M3-09）因暂无已认证小程序凭证，本轮只预留不实现。
 */
@Component
public class EventReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EventReminderJob.class);

    private final EventReminderService reminderService;

    public EventReminderJob(EventReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /** 每小时整点执行（cron: 秒 分 时 日 月 周） */
    @Scheduled(cron = "0 0 * * * ?")
    public void remind() {
        try {
            int sent = reminderService.scanAndNotify();
            if (sent > 0) {
                log.info("[M5] 定时任务：发出活动/竞赛提醒 {} 条", sent);
            }
        } catch (Exception e) {
            log.error("[M5] 活动/竞赛提醒扫描失败：{}", e.getMessage(), e);
        }
    }
}
