package com.tonghang.campus.content.api;

import com.tonghang.campus.content.dto.FeedItem;

import java.util.List;

/**
 * 商品信息查询门面（内容模块消费交易模块能力）。
 *
 * <p>来源：《13-API接口文档》§3.2「GET /home/feeds 首页推荐流（机会推荐 + 信息推荐）」，
 * 机会推荐部分取自 M4 在售商品。
 *
 * <p>解耦说明（《11》§2.1）：content 模块不直接依赖 trade 模块，
 * 由 campus-server 装配阶段把 trade 的实现注册为本接口 Bean；
 * 未装配时首页流仅返回动态（降级）。
 */
public interface GoodsQueryFacade {

    /** 取在售商品作为信息流条目（返回 contentType=2 的 FeedItem） */
    List<FeedItem> onSaleForFeed(int page, int size);

    /** 在售商品总数 */
    long onSaleCount();
}
