package com.njydsz.pmis.common.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * 类型安全的 JSON 工具（封装 fastjson2，消除调用方 {@code @SuppressWarnings("unchecked")}）
 *
 * <p>背景：直接调用 {@code JSON.parseObject(str, Map.class)} 返回原始 {@code Map} 类型，
 * 赋值给 {@code Map<String, Object>} 需要 unchecked cast。本工具类改用
 * {@link JSON#parseObject(String)} 返回的 {@link JSONObject}（其继承自
 * {@code Map<String, Object>}），从源头避免 unchecked 警告。
 *
 * <p>所有方法对 {@code null} 和空白字符串返回 {@code null}（或空集合），不会抛出 NPE。
 * 解析异常由调用方自行 try-catch（保持与原 fastjson2 行为一致）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class JsonUtils {

    private JsonUtils() {
    }

    /**
     * JSON 字符串 → {@code Map<String, Object>}
     *
     * <p>利用 fastjson2 的 {@link JSONObject} 继承自 {@code Map<String, Object>} 的特性，
     * 避免 {@code JSON.parseObject(str, Map.class)} 的 unchecked cast。
     *
     * @param json JSON 字符串，可为 null/空白
     * @return 解析后的 Map；输入为 null/空白时返回 null
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JSONObject obj = JSON.parseObject(json);
        return obj;
    }

    /**
     * JSON 字符串 → {@code List<Object>}
     *
     * @param json JSON 数组字符串，可为 null/空白
     * @return 解析后的 List；输入为 null/空白时返回 null
     */
    public static List<Object> parseList(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseArray(json);
    }

    /**
     * JSON 字符串 → 指定类型对象（委托给 fastjson2，类型安全）
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型 Class
     * @param <T>   目标类型
     * @return 解析后的对象；输入为 null/空白时返回 null
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }

    /**
     * JSON 字符串 → 复杂泛型类型对象（基于 {@link TypeReference}，类型安全）
     *
     * <p>示例：
     * <pre>{@code
     * Map<String, List<Integer>> m = JsonUtils.parseObject(json,
     *         new TypeReference<Map<String, List<Integer>>>() {});
     * }</pre>
     *
     * @param json JSON 字符串
     * @param type 目标类型 TypeReference
     * @param <T>  目标类型
     * @return 解析后的对象；输入为 null/空白时返回 null
     */
    public static <T> T parseObject(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.parseObject(json, type);
    }

    /**
     * 对象 → JSON 字符串
     *
     * @param obj 任意对象
     * @return JSON 字符串；输入为 null 时返回 null
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSON.toJSONString(obj);
    }
}
