package com.tonghang.campus.message.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 字段（skill_tags / hobby_tags / images / duty_plan 等）的轻量编解码。
 *
 * <p>说明：《12》中这些字段为 JSON 类型，MySQL 驱动返回字符串；
 * 统一以字符串存储 + 本工具转换，避免为每个字段单独配置 TypeHandler。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    /* ---------------- 序列化 ---------------- */

    /** 对象 → JSON 字符串 */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------------- 反序列化 ---------------- */

    /** JSON 字符串 → 字符串列表（解析失败返回空列表） */
    public static List<String> toStringList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** JSON 字符串 → 字符串数组 */
    public static List<Long> toLongList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** JSON 字符串 → Map（解析失败返回 null） */
    public static Map<String, Object> toMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 字符串 → 指定类型（解析失败返回 fallback） */
    public static <T> T parse(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.trim().isEmpty()) {
            return fallback;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 对外暴露 ObjectMapper（供需要自定义读写的场景） */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
