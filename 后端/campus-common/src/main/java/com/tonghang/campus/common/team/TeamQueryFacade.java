package com.tonghang.campus.common.team;

/**
 * 队伍只读查询门面（跨模块共享契约）。
 *
 * <p>来源：《13-API接口文档》§3.5「POST /competitions/{id}/enroll 报名竞赛（可关联 team_id）」——
 * 团队报名需校验「队伍存在 + 我是队员」，而 team / team_member 表归属 campus-message（M3）。
 *
 * <p>解耦说明（《11》§2.1「模块之间不得直接操作他域 Mapper」）：
 * <ul>
 *   <li>接口定义在 <b>campus-common</b>，避免 M5(event) 与 M3(message) 相互依赖形成循环；</li>
 *   <li>实现由 campus-message 提供（它持有 team 表）；</li>
 *   <li>使用方通过 {@code ObjectProvider} 可选注入，未装配时拒绝团队报名（不臆造校验通过）。</li>
 * </ul>
 */
public interface TeamQueryFacade {

    /** 队伍是否存在且未关闭 */
    boolean exists(Long teamId);

    /** 指定用户是否为该队伍成员（含队长） */
    boolean isMember(Long teamId, Long userId);

    /** 队伍名称（用于通知/展示文案；不存在返回 null） */
    String teamName(Long teamId);
}
