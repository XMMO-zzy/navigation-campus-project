package com.tonghang.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.account.api.UserFacade;
import com.tonghang.campus.common.api.ErrorCode;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.notify.NotifySender;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.content.api.GoodsQueryFacade;
import com.tonghang.campus.content.dto.FeedItem;
import com.tonghang.campus.content.support.SensitiveWordChecker;
import com.tonghang.campus.trade.entity.Goods;
import com.tonghang.campus.trade.entity.GoodsOrder;
import com.tonghang.campus.trade.entity.GoodsWanted;
import com.tonghang.campus.trade.mapper.GoodsMapper;
import com.tonghang.campus.trade.mapper.GoodsOrderMapper;
import com.tonghang.campus.trade.mapper.GoodsWantedMapper;
import com.tonghang.campus.trade.support.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 二手商品服务（M4）。
 *
 * <p>来源：《13-API接口文档》§3.4：
 * <ul>
 *   <li>POST /goods 发布商品（3 步向导数据一次提交）— FR-M4-01</li>
 *   <li>GET /goods?category=&cond=&min=&max= 商品列表 — FR-M4-02</li>
 *   <li>GET /goods/my?view= 我的视图 — FR-M4-03</li>
 *   <li>GET /goods/{id} 商品详情（联系方式按权限脱敏）— FR-M4-02</li>
 *   <li>POST /goods-wanted 发布求购 — FR-M4-06</li>
 *   <li>PUT|DELETE /goods/{id} 编辑 / 下架</li>
 * </ul>
 *
 * <p>另实现 {@link GoodsQueryFacade}，为 M2 首页推荐流提供在售商品（机会推荐）。
 */
@Service
public class GoodsService implements GoodsQueryFacade {

    private final GoodsMapper goodsMapper;
    private final GoodsOrderMapper orderMapper;
    private final GoodsWantedMapper wantedMapper;
    private final UserFacade userFacade;
    private final SensitiveWordChecker sensitiveWordChecker;
    private final NotifySender notifySender;

    public GoodsService(GoodsMapper goodsMapper,
                        GoodsOrderMapper orderMapper,
                        GoodsWantedMapper wantedMapper,
                        UserFacade userFacade,
                        SensitiveWordChecker sensitiveWordChecker,
                        NotifySender notifySender) {
        this.goodsMapper = goodsMapper;
        this.orderMapper = orderMapper;
        this.wantedMapper = wantedMapper;
        this.userFacade = userFacade;
        this.sensitiveWordChecker = sensitiveWordChecker;
        this.notifySender = notifySender;
    }

    /* ==================== 发布 / 编辑 / 下架 ==================== */

    /** 发布商品（FR-M4-01 / POST /goods） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String title = str(body.get("title"));
        if (title == null || title.length() > 60) {
            throw BizException.param("标题需为 1~60 个字符");
        }
        BigDecimal price = decimalOf(body.get("price"));
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.param("请填写正确的价格");
        }
        Long categoryId = longOrNull(body.get("categoryId"));
        if (categoryId == null) {
            throw BizException.param("请选择商品品类");
        }
        Integer cond = intOrNull(body.get("cond"));
        if (cond == null || cond < 1 || cond > 5) {
            throw BizException.param("成色取值需为 1~5");
        }

        // 机审：标题 + 简介
        String scanText = title + " " + (body.get("description") == null ? "" : String.valueOf(body.get("description")));
        SensitiveWordChecker.Result scan = sensitiveWordChecker.scan(scanText);
        if (scan.isBlocked()) {
            throw new BizException(ErrorCode.SENSITIVE_WORD,
                    "内容含违规词「" + scan.getHitWord() + "」，请修改后重试");
        }

        Goods g = new Goods();
        g.setUserId(userId);
        g.setTitle(title);
        g.setCategoryId(categoryId);
        g.setPrice(price);
        g.setOrigPrice(decimalOf(body.get("origPrice")));
        g.setCond(cond);
        g.setDims(str(body.get("dims")));
        g.setDescription(str(body.get("description")));
        g.setImages(JsonUtil.toJson(body.get("images")));
        g.setTradePlace(str(body.get("tradePlace")));
        g.setContact(str(body.get("contact")));
        g.setStatus(scan.isNeedReview() ? Goods.STATUS_PENDING : Goods.STATUS_ON_SALE);
        g.setViewCnt(0);
        g.setFavoriteCnt(0);
        goodsMapper.insert(g);

        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("goodsId", g.getId());
        data.put("status", g.getStatus());
        data.put("statusText", goodsStatusText(g.getStatus()));
        return data;
    }

    /** 编辑商品（PUT /goods/{id}，仅卖家） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long userId, Long id, Map<String, Object> body) {
        Goods g = requireOwned(userId, id);
        if (body != null) {
            if (body.get("title") != null) {
                String title = String.valueOf(body.get("title")).trim();
                if (title.isEmpty() || title.length() > 60) {
                    throw BizException.param("标题需为 1~60 个字符");
                }
                g.setTitle(title);
            }
            if (body.get("price") != null) {
                BigDecimal price = decimalOf(body.get("price"));
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw BizException.param("请填写正确的价格");
                }
                g.setPrice(price);
            }
            if (body.get("categoryId") != null) {
                g.setCategoryId(Long.parseLong(String.valueOf(body.get("categoryId"))));
            }
            if (body.get("cond") != null) {
                int cond = Integer.parseInt(String.valueOf(body.get("cond")));
                if (cond < 1 || cond > 5) {
                    throw BizException.param("成色取值需为 1~5");
                }
                g.setCond(cond);
            }
            if (body.get("description") != null) {
                g.setDescription(String.valueOf(body.get("description")));
            }
            if (body.get("images") != null) {
                g.setImages(JsonUtil.toJson(body.get("images")));
            }
            if (body.get("tradePlace") != null) {
                g.setTradePlace(String.valueOf(body.get("tradePlace")));
            }
            if (body.get("contact") != null) {
                g.setContact(String.valueOf(body.get("contact")));
            }
            if (body.get("status") != null) {
                int st = Integer.parseInt(String.valueOf(body.get("status")));
                // 仅允许 在售 ↔ 已下架 的手动切换，售出/删除由状态机驱动
                if (st != Goods.STATUS_ON_SALE && st != Goods.STATUS_OFF_SALE) {
                    throw BizException.param("仅支持切换为 在售(1) 或 已下架(2)");
                }
                g.setStatus(st);
            }
        }
        goodsMapper.updateById(g);
        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("goodsId", g.getId());
        data.put("status", g.getStatus());
        data.put("statusText", goodsStatusText(g.getStatus()));
        return data;
    }

    /** 下架 / 删除商品（DELETE /goods/{id}，仅卖家，软删） */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        requireOwned(userId, id);
        goodsMapper.deleteById(id);
    }

    /* ==================== 列表 / 详情 ==================== */

    /** 商品列表（FR-M4-02 / GET /goods） */
    public PageResult<Map<String, Object>> list(Long viewerId, Map<String, Object> params,
                                                Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<Goods> w = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, Goods.STATUS_ON_SALE);
        // 屏蔽双向不可见（FR-M2-11）：登录用户看不到与自己有任一方向拉黑关系的卖家商品
        if (viewerId != null) {
            Set<Long> blocked = userFacade.blockedEitherWay(viewerId);
            if (blocked != null && !blocked.isEmpty()) {
                w.notIn(Goods::getUserId, blocked);
            }
        }
        if (params != null) {
            Long category = longOrNull(params.get("category"));
            if (category != null) {
                w.eq(Goods::getCategoryId, category);
            }
            Integer cond = intOrNull(params.get("cond"));
            if (cond != null) {
                w.eq(Goods::getCond, cond);
            }
            BigDecimal min = decimalOf(params.get("min"));
            if (min != null) {
                w.ge(Goods::getPrice, min);
            }
            BigDecimal max = decimalOf(params.get("max"));
            if (max != null) {
                w.le(Goods::getPrice, max);
            }
            String kw = params.get("kw") == null ? null : String.valueOf(params.get("kw")).trim();
            if (kw != null && !kw.isEmpty()) {
                w.like(Goods::getTitle, kw);
            }
        }
        w.orderByDesc(Goods::getCreatedAt);

        Page<Goods> pageObj = new Page<>(p, s);
        goodsMapper.selectPage(pageObj, w);

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (Goods g : pageObj.getRecords()) {
            list.add(toListItem(g));
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /** 我的视图（FR-M4-03 / GET /goods/my?view=published|favorite|orders） */
    public PageResult<Map<String, Object>> my(Long userId, String view, Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);
        String v = view == null || view.trim().isEmpty() ? "published" : view.trim();

        if ("favorite".equals(v)) {
            // 收藏由内容域 favorite 表维护（多态）；跨域读取由 server 装配层聚合，
            // 骨架阶段返回空列表并保持结构一致
            return PageResult.empty(p, s);
        }
        if ("orders".equals(v)) {
            // 订单视图由 /orders?role= 提供，见 OrderService
            return PageResult.empty(p, s);
        }

        Page<Goods> pageObj = new Page<>(p, s);
        goodsMapper.selectPage(pageObj, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getUserId, userId)
                .orderByDesc(Goods::getId));
        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (Goods g : pageObj.getRecords()) {
            list.add(toListItem(g));
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /**
     * 商品详情（FR-M4-02 / GET /goods/{id}，联系方式按权限脱敏）。
     *
     * <p>脱敏规则（《10》FR-M4-02 + 《13》§3.4「下单后可见」）：
     * 非卖家且无有效订单 → contact 掩码；卖家本人或存在有效订单 → 明文。
     */
    public Map<String, Object> detail(Long viewerId, Long id) {
        Goods g = goodsMapper.selectById(id);
        if (g == null) {
            throw BizException.notFound("商品不存在或已下架");
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .setSql("view_cnt = view_cnt + 1"));

        Map<String, Object> m = toListItem(g);
        m.put("description", g.getDescription());
        m.put("origPrice", g.getOrigPrice());
        m.put("dims", g.getDims());
        m.put("tradePlace", g.getTradePlace());
        m.put("viewCnt", nvl(g.getViewCnt()));

        boolean isOwner = viewerId != null && g.getUserId().equals(viewerId);
        boolean contactVisible = isOwner;
        if (!contactVisible && viewerId != null) {
            // 存在有效订单（非取消/超时）即可见
            contactVisible = hasActiveOrder(viewerId, id);
        }
        m.put("contactVisible", contactVisible);
        m.put("contact", contactVisible ? g.getContact() : maskContact(g.getContact()));
        m.put("owner", isOwner);

        UserFacade.UserBrief u = userFacade.get(g.getUserId());
        Map<String, Object> seller = new LinkedHashMap<>(5);
        seller.put("id", g.getUserId());
        seller.put("nickname", u == null ? "用户" + g.getUserId() : u.getNickname());
        seller.put("avatar", u == null ? null : u.getAvatar());
        seller.put("schoolTag", u == null ? null : u.getSchoolTag());
        seller.put("creditScore", u == null ? null : u.getCreditScore());
        m.put("seller", seller);
        return m;
    }

    /** 发布求购（FR-M4-06 / POST /goods-wanted） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createWanted(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.param("请求体不能为空");
        }
        String title = str(body.get("title"));
        if (title == null || title.length() > 60) {
            throw BizException.param("求购标题需为 1~60 个字符");
        }
        GoodsWanted w = new GoodsWanted();
        w.setUserId(userId);
        w.setTitle(title);
        w.setKeywords(JsonUtil.toJson(body.get("keywords")));
        w.setDesc(str(body.get("desc")));
        w.setContact(str(body.get("contact")));
        w.setStatus(GoodsWanted.STATUS_VALID);
        wantedMapper.insert(w);

        // FR-M4-06 反向推送：按关键词匹配「发布过相关在售商品的用户」并推站内通知
        int notified = pushWantedToMatchedSellers(userId, w);

        Map<String, Object> data = new LinkedHashMap<>(3);
        data.put("wantedId", w.getId());
        data.put("status", w.getStatus());
        data.put("notified", notified);
        return data;
    }

    /** 单次反向推送的候选商品上限，避免一次求购扫描全表 */
    private static final int WANTED_MATCH_LIMIT = 200;

    /**
     * 求购帖反向推送（FR-M4-06 / T2.3）。
     *
     * <p>规则（按《10》FR-M4-06「按关键词匹配推送给发布过相关商品的用户」实现）：
     * <ol>
     *   <li>取求购帖 keywords，在**在售**商品标题上做 OR LIKE 匹配（排除求购者自己的商品）；</li>
     *   <li>命中的卖家去重后逐人发站内通知，dedupKey 带 wantedId 保证同一求购帖不重复打扰；</li>
     *   <li>返回实际通知人数，便于前端/排查观察触达效果。</li>
     * </ol>
     *
     * <p>注意：通知不设跳转目标（linkType=null）。原因是 `Enums.TargetType` 的 7 号位已被
     * 通知专用域（ORDER，见 notify_message.link_type）占用，而《12》§1.3 的「7 求购帖」
     * 属于 target_type 另一列的值域——这是已知的域混用问题，已在交付说明中登记，
     * 此处不臆造新的 linkType 取值。
     */
    private int pushWantedToMatchedSellers(Long wantedUserId, GoodsWanted wanted) {
        List<String> keywords = new ArrayList<>();
        for (String k : JsonUtil.toStringList(wanted.getKeywords())) {
            if (k != null && !k.trim().isEmpty()) {
                keywords.add(k.trim());
            }
        }
        if (keywords.isEmpty()) {
            return 0;
        }

        List<Goods> matched = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, Goods.STATUS_ON_SALE)
                .ne(Goods::getUserId, wantedUserId)
                .and(x -> {
                    for (int i = 0; i < keywords.size(); i++) {
                        if (i == 0) {
                            x.like(Goods::getTitle, keywords.get(0));
                        } else {
                            x.or().like(Goods::getTitle, keywords.get(i));
                        }
                    }
                })
                .last("LIMIT " + WANTED_MATCH_LIMIT));

        Set<Long> sellerIds = new LinkedHashSet<>();
        for (Goods g : matched) {
            if (g.getUserId() != null) {
                sellerIds.add(g.getUserId());
            }
        }

        int sent = 0;
        for (Long sellerId : sellerIds) {
            notifySender.send(sellerId, Enums.NotifyBiz.TRADE,
                    "有人求购相关商品",
                    "求购「" + wanted.getTitle() + "」与你的在售商品可能匹配，去看看",
                    null, null, "wanted-match:" + wanted.getId() + ":" + sellerId);
            sent++;
        }
        return sent;
    }

    /* ==================== GoodsQueryFacade（供 M2 首页流） ==================== */

    @Override
    public List<FeedItem> onSaleForFeed(int page, int size) {
        Page<Goods> pageObj = new Page<>(PageUtil.normalizePage(page), PageUtil.normalizeSize(size));
        goodsMapper.selectPage(pageObj, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, Goods.STATUS_ON_SALE)
                .orderByDesc(Goods::getCreatedAt));
        List<FeedItem> items = new ArrayList<>(pageObj.getRecords().size());
        for (Goods g : pageObj.getRecords()) {
            FeedItem item = new FeedItem();
            item.setContentType(FeedItem.TYPE_GOODS);
            item.setContentId(g.getId());
            item.setTitle(g.getTitle());
            item.setPrice(g.getPrice());
            item.setCond(condText(g.getCond()));
            item.setImage(firstImage(g.getImages()));
            Map<String, Object> cnt = new LinkedHashMap<>(2);
            cnt.put("favorite", nvl(g.getFavoriteCnt()));
            item.setCnt(cnt);
            item.setCreatedAt(g.getCreatedAt());
            UserFacade.UserBrief u = userFacade.get(g.getUserId());
            Map<String, Object> user = new LinkedHashMap<>(4);
            user.put("id", g.getUserId());
            user.put("nickname", u == null ? "用户" + g.getUserId() : u.getNickname());
            user.put("schoolTag", u == null ? null : u.getSchoolTag());
            item.setUser(user);
            items.add(item);
        }
        return items;
    }

    @Override
    public long onSaleCount() {
        Long cnt = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, Goods.STATUS_ON_SALE));
        return cnt == null ? 0L : cnt;
    }

    /* ==================== 内部工具 ==================== */

    private Goods requireOwned(Long userId, Long id) {
        Goods g = goodsMapper.selectById(id);
        if (g == null) {
            throw BizException.notFound("商品不存在或已删除");
        }
        if (!g.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return g;
    }

    /** 是否存在与买家之间的有效订单（用于联系方式可见性判定） */
    private boolean hasActiveOrder(Long buyerId, Long goodsId) {
        Long cnt = orderMapper.selectCount(new LambdaQueryWrapper<GoodsOrder>()
                .eq(GoodsOrder::getGoodsId, goodsId)
                .eq(GoodsOrder::getBuyerId, buyerId)
                .notIn(GoodsOrder::getStatus,
                        GoodsOrder.STATUS_CANCELED, GoodsOrder.STATUS_TIMEOUT));
        return cnt != null && cnt > 0;
    }

    private Map<String, Object> toListItem(Goods g) {
        Map<String, Object> m = new LinkedHashMap<>(10);
        m.put("id", g.getId());
        m.put("title", g.getTitle());
        m.put("price", g.getPrice());
        m.put("cond", g.getCond());
        m.put("condText", condText(g.getCond()));
        m.put("categoryId", g.getCategoryId());
        m.put("image", firstImage(g.getImages()));
        m.put("images", JsonUtil.toStringList(g.getImages()));
        m.put("status", g.getStatus());
        m.put("statusText", goodsStatusText(g.getStatus()));
        m.put("favoriteCnt", nvl(g.getFavoriteCnt()));
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    private String firstImage(String imagesJson) {
        List<String> list = JsonUtil.toStringList(imagesJson);
        return list.isEmpty() ? null : list.get(0);
    }

    private String condText(Integer cond) {
        if (cond == null) {
            return null;
        }
        switch (cond) {
            case 1:
                return "全新";
            case 2:
                return "九五新";
            case 3:
                return "九成新";
            case 4:
                return "八成新";
            default:
                return "七成新及以下";
        }
    }

    static String goodsStatusText(Integer status) {
        if (status == null) {
            return "待审";
        }
        switch (status) {
            case Goods.STATUS_ON_SALE:
                return "在售";
            case Goods.STATUS_OFF_SALE:
                return "已下架";
            case Goods.STATUS_SOLD:
                return "已售出";
            case Goods.STATUS_DELETED:
                return "已删除";
            case Goods.STATUS_RETURNED:
                return "审核退回";
            default:
                return "待审";
        }
    }

    /** 联系方式脱敏：保留前 3 后 4（手机号）；其它按首尾保留 */
    private String maskContact(String contact) {
        if (contact == null || contact.isEmpty()) {
            return contact;
        }
        if (contact.matches("^1[3-9]\\d{9}$")) {
            return contact.substring(0, 3) + "****" + contact.substring(7);
        }
        if (contact.length() <= 4) {
            return contact.charAt(0) + "***";
        }
        return contact.substring(0, 2) + "****" + contact.substring(contact.length() - 2);
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal decimalOf(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 收藏列表（供 server 层聚合 TARGET_TYPE=GOODS 的 favorite 记录） */
    public List<Map<String, Object>> favoriteGoods(List<Long> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Goods> list = goodsMapper.selectBatchIds(goodsIds);
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Goods g : list) {
            out.add(toListItem(g));
        }
        return out;
    }

    /** 品类字典键（与 Enums / sys_config 对齐） */
    public static String categoryKey(Long categoryId) {
        return categoryId == null ? null : "goods.category." + categoryId;
    }

    /** 供其它模块读取 Enums.TargetType 常量（避免硬编码） */
    public static int targetTypeGoods() {
        return Enums.TargetType.GOODS;
    }
}
