package com.njydsz.workflow.server.engine;

import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.collection.MapUtils;

/**
 * 工作流引擎 JSON 工具（基于 YdszJson 引擎，统一使用 YdszJson）
 *
 * <p>提供 Map 安全类型提取方法，替代原 JSONObject.getXxx() 调用。
 *
 * @since 1.0.0
 * @deprecated 使用 {@link YdszJson} 和 {@link MapUtils} 替代
 */
@Deprecated
public final class JsonHelper {

    private JsonHelper() {
    }

    /**
     * 对象 → JSON 字符串
     * 
     * @deprecated 使用 {@link YdszJson#toJson(Object)}
     */
    @Deprecated
    public static String toJson(Object obj) {
        return YdszJson.toJson(obj);
    }

    /**
     * JSON 字符串 → Map
     *
     * @param json JSON 字符串
     * @return 解析后的 Map；输入为 null/空白时返回 null
     * @deprecated 使用 {@link YdszJson#parseMap(String)}
     */
    @Deprecated
    public static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return YdszJson.parseMap(json);
    }

    // ============================== Map 安全类型提取 ==============================

    /**
     * 从 Map 中安全提取 String 值
     *
     * @param map Map 实例（可空）
     * @param key 键名
     * @return 字符串值；map 为 null 或 key 不存在时返回 null
     * @deprecated 使用 {@link MapUtils#getString(Map, String)}
     */
    @Deprecated
    public static String getString(Map<String, Object> map, String key) {
        return MapUtils.getString(map, key);
    }

    /**
     * 从 Map 中安全提取 Integer 值
     *
     * @param map Map 实例（可空）
     * @param key 键名
     * @return Integer 值；map 为 null 或 key 不存在或类型不匹配时返回 null
     * @deprecated 使用 {@link MapUtils#getInteger(Map, String)}
     */
    @Deprecated
    public static Integer getInteger(Map<String, Object> map, String key) {
        return MapUtils.getInteger(map, key);
    }

    /**
     * 从 Map 中安全提取 Long 值
     *
     * @param map Map 实例（可空）
     * @param key 键名
     * @return Long 值；map 为 null 或 key 不存在或类型不匹配时返回 null
     * @deprecated 使用 {@link MapUtils#getLong(Map, String)}
     */
    @Deprecated
    public static Long getLong(Map<String, Object> map, String key) {
        return MapUtils.getLong(map, key);
    }

    /**
     * 从 Map 中安全提取子 Map
     *
     * @param map Map 实例（可空）
     * @param key 键名
     * @return 子 Map；map 为 null 或 key 不存在或类型不匹配时返回 null
     * @deprecated 使用 {@link MapUtils#getMap(Map, String)}
     */
    @Deprecated
    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        return MapUtils.getMap(map, key);
    }

    /**
     * 从 Map 中安全提取 List
     *
     * @param map Map 实例（可空）
     * @param key 键名
     * @return List；map 为 null 或 key 不存在或类型不匹配时返回 null
     * @deprecated 使用 {@link MapUtils#getList(Map, String)}
     */
    @Deprecated
    public static List<Object> getList(Map<String, Object> map, String key) {
        return MapUtils.getList(map, key);
    }

    /**
     * 从 List 中安全提取指定索引的子 Map
     *
     * @param list List 实例（可空）
     * @param index 索引
     * @return 子 Map；list 为 null 或索引越界或类型不匹配时返回 null
     * @deprecated 使用 {@link MapUtils#getMapFromList(List, int)}
     */
    @Deprecated
    public static Map<String, Object> getMapFromList(List<Object> list, int index) {
        return MapUtils.getMapFromList(list, index);
    }

    /**
     * 将 Map&lt;?&gt; 安全转换为 Map&lt;String, Object&gt;
     *
     * @param m 原始 Map（可空）
     * @return 转换后的 Map；输入为 null 时返回 null
     * @deprecated 使用 {@link MapUtils#toStringObjectMap(Map)}
     */
    @Deprecated
    public static Map<String, Object> toStringObjectMap(Map<?, ?> m) {
        return MapUtils.toStringObjectMap(m);
    }

    /**
     * 安全地将 Object 转为指定元素类型的 List
     *
     * @param raw 原始对象（通常是 List&lt;?&gt;）
     * @param clazz 元素类型
     * @return 类型安全的 List；输入为 null 或不是 List 时返回空 List
     * @deprecated 使用 {@link MapUtils#safeCastList(Object, Class)}
     */
    @Deprecated
    public static <T> List<T> safeCastList(Object raw, Class<T> clazz) {
        return MapUtils.safeCastList(raw, clazz);
    }

    /**
     * 安全地将 Object 转为 Map&lt;String, Object&gt;
     *
     * @param raw 原始对象（通常是 Map&lt;?&gt;）
     * @return 类型安全的 Map；输入为 null 或不是 Map 时返回 null
     * @deprecated 使用 {@link MapUtils#safeCastMap(Object)}
     */
    @Deprecated
    public static Map<String, Object> safeCastMap(Object raw) {
        return MapUtils.safeCastMap(raw);
    }

    /**
     * 从 Map 中安全提取 List&lt;Map&lt;String, Object&gt;&gt;
     *
     * @param map Map 实例（可空）
     * @param key 键名
     * @return List of Map；map 为 null 或 key 不存在时返回 null
     * @deprecated 使用 {@link MapUtils#getListOfMaps(Map, String)}
     */
    @Deprecated
    public static List<Map<String, Object>> getListOfMaps(Map<String, Object> map, String key) {
        return MapUtils.getListOfMaps(map, key);
    }
}
