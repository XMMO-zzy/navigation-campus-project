package com.tonghang.campus.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.favorite.FavoriteQueryFacade;
import com.tonghang.campus.common.notify.NotifySender;
import com.tonghang.campus.event.entity.Activity;
import com.tonghang.campus.event.entity.Competition;
import com.tonghang.campus.event.mapper.ActivityMapper;
import com.tonghang.campus.event.mapper.CompetitionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动与赛事提醒服务（M5 / FR-M3-05 / T2.5）。
 *
 * <p>来源：《10-需求文档》FR-M3-05「活动与赛事提醒：报名开始、即将截止（剩余时间）、
 * 赛程与材料提交、规则变化（含官方来源）——全部自动化系统消息」。
 *
 * <p><b>提醒受众的确定方式</b>（重要，属本轮实现口径，已在交付说明登记待评审）：
 * 本项目决策③确定「关注（follow）接口不做」，项目中不存在关注关系图。
 * 在既有数据里，**收藏该活动/竞赛的用户**是唯一能表达「感兴趣」的记录
 * （《12》§2.2 favorite 支持 target_type = 3 活动 / 4 竞赛），故以收藏者为受众。
 * 若后续「关注」体系上线，把 {@code FavoriteQueryFacade} 换成关注关系即可，
 * 本类其余逻辑无需改动。
 *
 * <p>去重：所有通知都带 dedupKey，{@code NotifySender} 侧按「用户+业务+日期」唯一，
 * 保证同一活动同一天不会重复打扰（即使任务每小时跑一次）。
 */
@Service
public class EventReminderService {

    private static final Logger log = LoggerFactory.getLogger(EventReminderService.class);

    /** 「即将截止」的提前提醒窗口（小时） */
    private static final int DEADLINE_AHEAD_HOURS = 24;

    /** 「报名开始」的命中窗口（小时）：任务每小时跑一次，取最近 1 小时内的开始时间 */
    private static final int ENROLL_START_WINDOW_HOURS = 1;

    /** 单次扫描处理的活动/竞赛上限 */
    private static final int SCAN_LIMIT = 200;

    private final ActivityMapper activityMapper;
    private final CompetitionMapper competitionMapper;
    private final NotifySender notifySender;
    private final ObjectProvider<FavoriteQueryFacade> favoriteFacadeProvider;

    public EventReminderService(ActivityMapper activityMapper,
                                CompetitionMapper competitionMapper,
                                NotifySender notifySender,
                                ObjectProvider<FavoriteQueryFacade> favoriteFacadeProvider) {
        this.activityMapper = activityMapper;
        this.competitionMapper = competitionMapper;
        this.notifySender = notifySender;
        this.favoriteFacadeProvider = favoriteFacadeProvider;
    }

    /**
     * 扫描并发送提醒。
     *
     * @return 实际发出的通知条数
     */
    public int scanAndNotify() {
        FavoriteQueryFacade favorites = favoriteFacadeProvider.getIfAvailable();
        if (favorites == null) {
            // 收藏门面未装配（例如只启动 event 模块）→ 无法确定受众，直接跳过而非群发
            log.debug("[M5] 收藏门面未装配，跳过活动提醒扫描");
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int sent = scanActivities(favorites, now);
        sent += scanCompetitions(favorites, now);
        return sent;
    }

    /* ==================== 活动 ==================== */

    private int scanActivities(FavoriteQueryFacade favorites, LocalDateTime now) {
        int sent = 0;

        // 1) 报名开始：enroll_start 落在过去 1 小时内，且仍在报名中
        List<Activity> started = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, Activity.STATUS_ENROLLING)
                .isNotNull(Activity::getEnrollStart)
                .ge(Activity::getEnrollStart, now.minusHours(ENROLL_START_WINDOW_HOURS))
                .le(Activity::getEnrollStart, now)
                .last("LIMIT " + SCAN_LIMIT));
        for (Activity a : started) {
            sent += notifyUsers(favorites, Enums.TargetType.ACTIVITY, a.getId(),
                    "活动开始报名：" + a.getTitle(),
                    "「" + a.getTitle() + "」已开放报名，截止 " + fmt(a.getEnrollDeadline()),
                    "activity-start:" + a.getId());
        }

        // 2) 即将截止：截止时间落在未来 24 小时内，且仍在报名中
        List<Activity> closing = activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, Activity.STATUS_ENROLLING)
                .isNotNull(Activity::getEnrollDeadline)
                .gt(Activity::getEnrollDeadline, now)
                .le(Activity::getEnrollDeadline, now.plusHours(DEADLINE_AHEAD_HOURS))
                .last("LIMIT " + SCAN_LIMIT));
        for (Activity a : closing) {
            sent += notifyUsers(favorites, Enums.TargetType.ACTIVITY, a.getId(),
                    "报名即将截止：" + a.getTitle(),
                    "「" + a.getTitle() + "」还剩 " + leftText(now, a.getEnrollDeadline()) + "截止报名",
                    "activity-deadline:" + a.getId());
        }
        return sent;
    }

    /* ==================== 竞赛 ==================== */

    private int scanCompetitions(FavoriteQueryFacade favorites, LocalDateTime now) {
        int sent = 0;

        // 1) 报名开始
        List<Competition> started = competitionMapper.selectList(new LambdaQueryWrapper<Competition>()
                .eq(Competition::getStatus, Competition.STATUS_ENROLLING)
                .isNotNull(Competition::getEnrollStart)
                .ge(Competition::getEnrollStart, now.minusHours(ENROLL_START_WINDOW_HOURS))
                .le(Competition::getEnrollStart, now)
                .last("LIMIT " + SCAN_LIMIT));
        for (Competition c : started) {
            sent += notifyUsers(favorites, Enums.TargetType.COMPETITION, c.getId(),
                    "竞赛开始报名：" + c.getTitle(),
                    "「" + c.getTitle() + "」已开放报名，截止 " + fmt(c.getEnrollDeadline()),
                    "competition-start:" + c.getId());
        }

        // 2) 即将截止
        List<Competition> closing = competitionMapper.selectList(new LambdaQueryWrapper<Competition>()
                .eq(Competition::getStatus, Competition.STATUS_ENROLLING)
                .isNotNull(Competition::getEnrollDeadline)
                .gt(Competition::getEnrollDeadline, now)
                .le(Competition::getEnrollDeadline, now.plusHours(DEADLINE_AHEAD_HOURS))
                .last("LIMIT " + SCAN_LIMIT));
        for (Competition c : closing) {
            sent += notifyUsers(favorites, Enums.TargetType.COMPETITION, c.getId(),
                    "报名即将截止：" + c.getTitle(),
                    "「" + c.getTitle() + "」还剩 " + leftText(now, c.getEnrollDeadline()) + "截止报名",
                    "competition-deadline:" + c.getId());
        }

        // 3) 赛程提醒：初赛/决赛落在未来 24 小时内（FR-M3-05「赛程与材料提交」）
        List<Competition> upcoming = competitionMapper.selectList(new LambdaQueryWrapper<Competition>()
                .in(Competition::getStatus, Competition.STATUS_ENROLLING, Competition.STATUS_ONGOING)
                .isNotNull(Competition::getPreliminaryAt)
                .gt(Competition::getPreliminaryAt, now)
                .le(Competition::getPreliminaryAt, now.plusHours(DEADLINE_AHEAD_HOURS))
                .last("LIMIT " + SCAN_LIMIT));
        for (Competition c : upcoming) {
            sent += notifyUsers(favorites, Enums.TargetType.COMPETITION, c.getId(),
                    "赛程提醒：" + c.getTitle(),
                    "「" + c.getTitle() + "」初赛将于 " + fmt(c.getPreliminaryAt()) + " 开始，请准备材料",
                    "competition-preliminary:" + c.getId());
        }
        return sent;
    }

    /* ==================== 内部工具 ==================== */

    /** 向「收藏了该目标」的用户群发提醒（去重由 NotifySender 的 dedupKey 保证） */
    private int notifyUsers(FavoriteQueryFacade favorites, int targetType, Long targetId,
                            String title, String content, String dedupPrefix) {
        List<Long> userIds = favorites.usersWhoFavorited(targetType, targetId);
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        notifySender.sendBatch(userIds, Enums.NotifyBiz.EVENT, title, content,
                targetType, targetId, dedupPrefix);
        return userIds.size();
    }

    /** 剩余时间文案（小时/天） */
    private String leftText(LocalDateTime now, LocalDateTime deadline) {
        if (deadline == null) {
            return "";
        }
        Duration d = Duration.between(now, deadline);
        long hours = Math.max(1L, d.toHours());
        return hours >= 24 ? (hours / 24) + " 天" : hours + " 小时";
    }

    private String fmt(LocalDateTime t) {
        if (t == null) {
            return "待定";
        }
        return t.toLocalDate() + " " + String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
