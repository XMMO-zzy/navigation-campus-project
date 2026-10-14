package com.tonghang.campus.common.favorite;

import java.util.List;

/**
 * 收藏关系只读门面（跨模块共享契约）。
 *
 * <p>用途：M5/M6 的「活动与赛事提醒」需要一个**数据可支撑的受众范围**
 * （《10》FR-M3-05：报名开始、即将截止等自动化提醒）。
 *
 * <p>为什么用收藏：本项目决策③明确「关注（follow）相关接口不做」，
 * 因此不存在关注关系图。在可用数据里，**收藏活动/竞赛**是唯一能表达
 * 「用户对该活动感兴趣」的既有记录（《12》§2.2 favorite 支持
 * target_type = 3 活动 / 4 竞赛），故以其作为提醒受众。
 *
 * <p>解耦说明（《11》§2.1）：接口定义在 campus-common，实现由 campus-content 提供
 * （它持有 favorite 表），使用方通过 {@code ObjectProvider} 可选注入。
 */
public interface FavoriteQueryFacade {

    /**
     * 取收藏了指定目标的用户 id。
     *
     * @param targetType 多态目标类型，见 {@code Enums.TargetType}（活动=3 / 竞赛=4）
     * @param targetId   目标 id
     * @return 去重后的用户 id 列表；无数据返回空列表（不返回 null）
     */
    List<Long> usersWhoFavorited(int targetType, Long targetId);
}
