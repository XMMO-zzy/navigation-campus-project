package com.tonghang.campus.common.search;

import java.util.List;
import java.util.Map;

/**
 * 站内消息检索门面（跨模块共享契约）。
 *
 * <p>来源：《13-API接口文档》§3.2「GET /search?kw=&amp;type= 全局搜索（动态/帖子/重要消息/校园话题）」
 * 与《10》FR-M2-07 —— 其中「重要消息」类搜索结果取自 M3 的消息/待办域。
 *
 * <p>解耦说明（《11》§2.1 模块之间不得直接操作他域 Mapper）：
 * <ul>
 *   <li>接口定义在 <b>campus-common</b>，避免 M2(content) 与 M3(message) 相互依赖形成循环；</li>
 *   <li>实现由 campus-message 提供（它持有 notify_message 表）；</li>
 *   <li>使用方通过 {@code ObjectProvider} 可选注入，未装配时返回空列表而非臆造数据。</li>
 * </ul>
 */
public interface MessageSearchFacade {

    /**
     * 在指定用户的消息中检索关键词。
     *
     * <p>只返回该用户自己的消息（含业务通知与系统通知），按时间倒序。
     * 返回元素应与全局搜索的通用结构对齐，至少包含：
     * {@code contentType(10=消息) / contentId / title / text / createdAt}。
     *
     * @param userId  检索范围所属用户
     * @param keyword 关键词（非空）
     * @return 命中列表（无命中返回空列表，不返回 null）
     */
    List<Map<String, Object>> searchMessages(Long userId, String keyword);
}
