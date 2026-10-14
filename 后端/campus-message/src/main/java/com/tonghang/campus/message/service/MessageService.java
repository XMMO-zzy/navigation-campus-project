package com.tonghang.campus.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tonghang.campus.common.api.PageResult;
import com.tonghang.campus.common.constant.Enums;
import com.tonghang.campus.common.exception.BizException;
import com.tonghang.campus.common.redis.RedisKeys;
import com.tonghang.campus.common.redis.RedisSupport;
import com.tonghang.campus.common.util.PageUtil;
import com.tonghang.campus.message.entity.NotifyMessage;
import com.tonghang.campus.message.mapper.NotifyMessageMapper;
import com.tonghang.campus.message.support.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息服务（M3）。
 *
 * <p>来源：《13-API接口文档》§3.3：
 * <ul>
 *   <li>GET /messages?box=&biz= 通知列表 — FR-M3-01</li>
 *   <li>PUT /messages/read-all 全部已读 — FR-M3-02</li>
 *   <li>GET /messages/badge 各分类未读数（Tab 角标聚合）— FR-M3-08</li>
 *   <li>GET/PUT /messages/settings 消息分类开关 + 免打扰 — FR-M3-02</li>
 * </ul>
 *
 * <p>设置项持久化说明：已与用户确认「不改《12》表结构，用 Redis + 本地存储」，
 * 故消息设置以用户为粒度落 Redis（JSON 字符串，键见 {@link RedisKeys#notifySetting}），
 * 不再随进程重启丢失。Redis 不可用时自动退回默认值（全开 / 免打扰关闭）。
 */
@Service
public class MessageService {

    private final NotifyMessageMapper mapper;
    private final RedisSupport redis;

    public MessageService(NotifyMessageMapper mapper, RedisSupport redis) {
        this.mapper = mapper;
        this.redis = redis;
    }

    /**
     * 通知列表（FR-M3-01 / GET /messages）。
     *
     * @param box all / unread
     * @param biz '' / activity / competition / trade / buddy / system
     */
    public PageResult<Map<String, Object>> list(Long userId, String box, String biz,
                                                Integer page, Integer size) {
        int p = PageUtil.normalizePage(page);
        int s = PageUtil.normalizeSize(size);

        LambdaQueryWrapper<NotifyMessage> w = new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .orderByDesc(NotifyMessage::getId);

        if ("unread".equalsIgnoreCase(box)) {
            w.eq(NotifyMessage::getIsRead, NotifyMessage.UNREAD);
        }
        Integer bizType = bizTypeOf(biz);
        if (bizType != null) {
            w.eq(NotifyMessage::getBizType, bizType);
        }

        Page<NotifyMessage> pageObj = new Page<>(p, s);
        mapper.selectPage(pageObj, w);

        List<Map<String, Object>> list = new ArrayList<>(pageObj.getRecords().size());
        for (NotifyMessage m : pageObj.getRecords()) {
            list.add(toMap(m));
        }
        return PageResult.of(list, pageObj.getTotal(), p, s);
    }

    /** 全部已读（FR-M3-02 / PUT /messages/read-all） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> readAll(Long userId, String biz) {
        Integer bizType = bizTypeOf(biz);
        LambdaQueryWrapper<NotifyMessage> w = new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, NotifyMessage.UNREAD);
        if (bizType != null) {
            w.eq(NotifyMessage::getBizType, bizType);
        }
        List<NotifyMessage> list = mapper.selectList(w);
        LocalDateTime now = LocalDateTime.now();
        for (NotifyMessage m : list) {
            m.setIsRead(NotifyMessage.READ);
            m.setReadAt(now);
            mapper.updateById(m);
        }
        Map<String, Object> data = new LinkedHashMap<>(2);
        data.put("updated", list.size());
        return data;
    }

    /** 单条已读（转已读，供详情页调用） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> read(Long userId, Long id) {
        NotifyMessage m = mapper.selectById(id);
        if (m == null || !m.getUserId().equals(userId)) {
            throw BizException.notFound("消息不存在");
        }
        if (m.getIsRead() == null || m.getIsRead() == NotifyMessage.UNREAD) {
            m.setIsRead(NotifyMessage.READ);
            m.setReadAt(LocalDateTime.now());
            mapper.updateById(m);
        }
        return toMap(m);
    }

    /**
     * 各分类未读数（FR-M3-08 / GET /messages/badge）。
     *
     * <p>返回结构：{@code { total, activity, competition, trade, buddy, system }}
     * —— 与前端 Tab 角标聚合口径一致。
     */
    public Map<String, Object> badge(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>(8);
        int total = 0;
        Map<Integer, Integer> byBiz = new LinkedHashMap<>();
        List<NotifyMessage> unread = mapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, NotifyMessage.UNREAD)
                .select(NotifyMessage::getBizType));
        for (NotifyMessage m : unread) {
            int b = m.getBizType() == null ? Enums.NotifyBiz.SYSTEM : m.getBizType();
            byBiz.merge(b, 1, Integer::sum);
            total++;
        }
        data.put("total", total);
        data.put("trade", byBiz.getOrDefault(Enums.NotifyBiz.TRADE, 0));
        data.put("team", byBiz.getOrDefault(Enums.NotifyBiz.TEAM, 0));
        data.put("interact", byBiz.getOrDefault(Enums.NotifyBiz.INTERACT, 0));
        data.put("system", byBiz.getOrDefault(Enums.NotifyBiz.SYSTEM, 0)
                + byBiz.getOrDefault(Enums.NotifyBiz.PLATFORM, 0));
        data.put("activity", byBiz.getOrDefault(Enums.NotifyBiz.EVENT, 0));
        data.put("competition", byBiz.getOrDefault(Enums.NotifyBiz.EVENT, 0));
        data.put("buddy", byBiz.getOrDefault(Enums.NotifyBiz.INTERACT, 0));
        return data;
    }

    /**
     * 消息设置（FR-M3-02 / GET/PUT /messages/settings）。
     *
     * <p>契约缺口说明：《12》未设计消息设置表，字段按《13》§3.3 语义给出默认值；
     * 落库待《12》补表（draft 之外需新增 user_message_setting）。
     */
    public Map<String, Object> getSettings(Long userId) {
        Map<String, Object> m = defaultSettings();
        if (userId == null) {
            return m;
        }
        String json = redis.call(t -> t.opsForValue().get(RedisKeys.notifySetting(userId)), null);
        if (json == null || json.trim().isEmpty()) {
            return m;
        }
        Map<String, Object> saved = JsonUtil.toMap(json);
        if (saved == null || saved.isEmpty()) {
            return m;
        }
        // 以默认值为底、已保存值为准：后续新增设置项也能自动获得默认值
        m.putAll(saved);
        return m;
    }

    /** 设置默认值（FR-M3-02：分类开关 + 免打扰时段） */
    private Map<String, Object> defaultSettings() {
        Map<String, Object> m = new LinkedHashMap<>(10);
        m.put("tradeEnabled", true);
        m.put("teamEnabled", true);
        m.put("interactEnabled", true);
        m.put("eventEnabled", true);
        m.put("systemEnabled", true);
        m.put("dndEnabled", false);
        m.put("dndStart", "22:00");
        m.put("dndEnd", "08:00");
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateSettings(Long userId, Map<String, Object> body) {
        Map<String, Object> m = getSettings(userId);
        if (body != null) {
            for (String k : new String[]{"tradeEnabled", "teamEnabled", "interactEnabled",
                    "eventEnabled", "systemEnabled", "dndEnabled"}) {
                if (body.get(k) != null) {
                    m.put(k, Boolean.parseBoolean(String.valueOf(body.get(k))));
                }
            }
            if (body.get("dndStart") != null) {
                m.put("dndStart", normalizeClock(String.valueOf(body.get("dndStart"))));
            }
            if (body.get("dndEnd") != null) {
                m.put("dndEnd", normalizeClock(String.valueOf(body.get("dndEnd"))));
            }
        }
        m.put("updatedAt", LocalDateTime.now());

        // 落 Redis（剔除 LocalDateTime 字段，避免默认 ObjectMapper 无法序列化 java.time 导致整串丢失）
        Map<String, Object> persist = new LinkedHashMap<>(m);
        persist.remove("updatedAt");
        redis.run(t -> t.opsForValue().set(RedisKeys.notifySetting(userId), JsonUtil.toJson(persist)));
        return m;
    }

    /**
     * 当前是否处于免打扰时段（FR-M3-02）。
     *
     * <p>支持跨零点区间（如 22:00–08:00）。Redis 不可用或未开启免打扰时返回 false。
     * <p>说明：当前站内通知无独立推送通道，此方法供后续「微信模板消息兜底」（FR-M3-09）
     * 接入后做抑制判断；现阶段仅落库与查询，不做假的拦截行为。
     */
    public boolean isInDnd(Long userId) {
        if (userId == null) {
            return false;
        }
        Map<String, Object> s = getSettings(userId);
        if (!Boolean.parseBoolean(String.valueOf(s.get("dndEnabled")))) {
            return false;
        }
        int now = LocalDateTime.now().getHour() * 60 + LocalDateTime.now().getMinute();
        int start = toMinutes(String.valueOf(s.get("dndStart")), 22 * 60);
        int end = toMinutes(String.valueOf(s.get("dndEnd")), 8 * 60);
        if (start == end) {
            return false;
        }
        return start < end ? (now >= start && now < end) : (now >= start || now < end);
    }

    /** 校验并归一化 "HH:mm" */
    private String normalizeClock(String v) {
        if (v == null || !v.trim().matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            throw BizException.param("免打扰时间格式需为 HH:mm（24 小时制）");
        }
        return v.trim();
    }

    private int toMinutes(String hhmm, int fallback) {
        if (hhmm == null || !hhmm.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            return fallback;
        }
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    /* ==================== 供其它模块使用 ==================== */

    /** 未读总数（首页 Tab 角标） */
    public long unreadCount(Long userId) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<NotifyMessage>()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, NotifyMessage.UNREAD));
        return cnt == null ? 0L : cnt;
    }

    /* ==================== 内部工具 ==================== */

    private Map<String, Object> toMap(NotifyMessage m) {
        Map<String, Object> item = new LinkedHashMap<>(8);
        item.put("id", m.getId());
        item.put("bizType", m.getBizType());
        item.put("bizText", bizText(m.getBizType()));
        item.put("title", m.getTitle());
        item.put("content", m.getContent());
        item.put("isRead", m.getIsRead() != null && m.getIsRead() == NotifyMessage.READ);
        item.put("linkType", m.getLinkType());
        item.put("linkId", m.getLinkId());
        item.put("createdAt", m.getCreatedAt());
        return item;
    }

    private String bizText(Integer bizType) {
        if (bizType == null) {
            return "系统";
        }
        switch (bizType) {
            case Enums.NotifyBiz.TRADE:
                return "交易";
            case Enums.NotifyBiz.TEAM:
                return "组队";
            case Enums.NotifyBiz.INTERACT:
                return "互动";
            case Enums.NotifyBiz.EVENT:
                return "活动竞赛";
            case Enums.NotifyBiz.PLATFORM:
                return "平台";
            default:
                return "系统";
        }
    }

    /** 前端 biz 参数 → 存储值（《13》§3.3 biz=activity/competition/trade/buddy/system） */
    private Integer bizTypeOf(String biz) {
        if (biz == null || biz.trim().isEmpty()) {
            return null;
        }
        switch (biz.trim()) {
            case "trade":
                return Enums.NotifyBiz.TRADE;
            case "buddy":
            case "interact":
                return Enums.NotifyBiz.INTERACT;
            case "system":
                return Enums.NotifyBiz.SYSTEM;
            case "activity":
            case "competition":
                return Enums.NotifyBiz.EVENT;
            default:
                return null;
        }
    }
}
