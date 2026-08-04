package com.remisoft.common.util.collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Map 工具类
 *
 * <p>聚焦于 JSON Map 解析场景下的类型安全读取与归一化，提供 null 安全的取值方法。
 * 典型用途：JSON 反序列化后得到 {@code Map<String, Object>} 或 {@code Map<?, ?>}，
 * 调用本类方法按 key 安全取出 String / Integer / Long / Boolean / Map / List 值。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>判空检查：isEmpty / isNotEmpty（null 安全）</li>
 *   <li>类型安全取值：getString / getInteger / getLong / getBoolean / getMap / getList</li>
 *   <li>JSON Map 归一化：toStringObjectMap / safeCastMap / safeCastList</li>
 *   <li>嵌套 JSON 解析：getListOfMaps / getMapFromList</li>
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK / Stream API）：</b>
 * <ul>
 *   <li>Map 创建 → {@code new HashMap<>()} / {@code new LinkedHashMap<>()} / {@link Map#of(Object, Object)}</li>
 *   <li>Map 转换/过滤 → {@link java.util.Map#replaceAll(java.util.function.BiFunction)} / stream</li>
 *   <li>Map 合并 → {@link Map#merge(Object, Object, java.util.function.BiFunction)} / {@code new HashMap<>(m1) {{ putAll(m2); }}</code>}</li>
 *   <li>Map 排序 → {@link java.util.TreeMap} / stream + {@link java.util.LinkedHashMap}</li>
 *   <li>Map 反转/扁平化/深拷贝 → stream 自行实现</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class MapUtils {

    private MapUtils() {
        throw new UnsupportedOperationException("MapUtils is a utility class and cannot be instantiated");
    }

    // ==================== 判空方法 ====================

    /**
     * 判断 Map 是否为空（null 安全）
     *
     * @param map Map 对象
     * @return 如果为 null 或 empty 返回 true
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Map 是否不为空（null 安全）
     *
     * @see #isEmpty(Map)
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    // ==================== 类型安全取值方法 ====================

    /**
     * 获取 String 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return String 值（调用 toString），map 为空或 key 不存在返回 null
     */
    public static String getString(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return value != null ? value.toString() : null;
    }

    /**
     * 获取 Integer 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Integer 值，转换失败返回 null
     */
    public static Integer getInteger(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toInteger(value);
    }

    /**
     * 获取 Long 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Long 值，转换失败返回 null
     */
    public static Long getLong(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toLong(value);
    }

    /**
     * 获取 Boolean 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Boolean 值，转换失败返回 null
     */
    public static Boolean getBoolean(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toBoolean(value);
    }

    /**
     * 获取 Map 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Map 值，非 Map 类型返回 null
     */
    public static Map<?, ?> getMap(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    /**
     * 获取 List 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return List 值，非 List 类型返回 null
     */
    public static List<?> getList(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return value instanceof List ? (List<?>) value : null;
    }

    // ==================== JSON Map 归一化方法 ====================

    /**
     * 将 {@code Map<?,?>} 安全转换为 {@code Map<String, Object>}。
     *
     * <p>用于 JSON 反序列化后 Map 的类型归一化：当 JSON 解析器返回
     * {@code Map<?, ?>}（如 FastJSON / Jackson 的默认行为）时，
     * 调用本方法将其转换为 {@code Map<String, Object>} 以便业务使用。
     *
     * <p>会创建新的 LinkedHashMap 并逐条复制（类型安全）；
     * 若需要深拷贝嵌套 Map 请使用 stream 自行实现。
     *
     * @param map 原始 Map（可为 null）
     * @return 转换后的 Map；入参为 null 时返回空 Map
     */
    public static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        if (map == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /**
     * 安全将 {@code Object} 强转为 {@code Map<String, Object>}。
     *
     * <p>典型场景：从 JSON Map 中按 key 取出一个 Object 字段（值为
     * {@code Map<?, ?>}），需要将其归一化为 {@code Map<String, Object>}。
     *
     * @param obj 原始对象
     * @return 强转后的 Map；入参为 null 或非 Map 时返回 null
     */
    public static Map<String, Object> safeCastMap(Object obj) {
        if (!(obj instanceof Map<?, ?> raw)) {
            return null;
        }
        return toStringObjectMap(raw);
    }

    /**
     * 安全将 {@code Object} 强转为 {@code List<T>}。
     *
     * <p>典型场景：从 JSON Map 中按 key 取出一个 List 字段（值为
     * {@code List<?>} 或 {@code List<Map<String,Object>>}），需要按元素类型逐个 cast。
     *
     * <p>入参为 null / 非 List 时返回空 List（不抛异常）。
     * 元素类型不匹配时跳过该元素（不抛 ClassCastException）。
     *
     * <p>返回的 List 始终为可变 {@link ArrayList}（包括空 List 情况），
     * 调用方可以安全地进行增删操作。
     *
     * @param obj     原始对象
     * @param element 元素类型
     * @return 类型安全的可变 List
     */
    public static <T> List<T> safeCastList(Object obj, Class<T> element) {
        if (!(obj instanceof List<?> raw)) {
            return new ArrayList<>();
        }
        List<T> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (element.isInstance(item)) {
                result.add(element.cast(item));
            }
        }
        return result;
    }

    // ==================== 嵌套 JSON 解析方法 ====================

    /**
     * 从 Map 中按 key 获取 {@code List<Map<String, Object>>} 值。
     *
     * <p>用于解析嵌套 JSON Map：取出某个 key 对应的 List，
     * 其中每个元素强制为 {@code Map<String, Object>}。
     * 入参为 null / 非 List / 元素非 Map 时返回空 List。
     *
     * @param map 原始 Map
     * @param key 键
     * @return List of Map；不可变空 List 表示取不到
     */
    public static List<Map<String, Object>> getListOfMaps(Map<String, Object> map, String key) {
        if (isEmpty(map) || key == null) {
            return List.of();
        }
        Object val = map.get(key);
        if (!(val instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Map<?, ?> m) {
                result.add(toStringObjectMap(m));
            }
        }
        return result;
    }

    /**
     * 从 List 中按下标取出元素并转换为 {@code Map<String, Object>}。
     *
     * <p>典型场景：JSON 反序列化后得到 {@code List<?>}（如 BPMN 节点列表），
     * 需要按下标取出每个元素并归一化为 {@code Map<String, Object>} 以便业务读取字段。
     *
     * <p>入参为 null / 下标越界 / 元素非 Map 时返回 null（不抛异常）。
     *
     * @param list  原始 List
     * @param index 元素下标
     * @return 强转后的 Map；取不到时返回 null
     */
    public static Map<String, Object> getMapFromList(List<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        Object item = list.get(index);
        return safeCastMap(item);
    }

    // ==================== 类型转换辅助方法 ====================

    /**
     * 转换为 Integer
     *
     * @param value 值
     * @return Integer 值，转换失败返回 null
     */
    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 转换为 Long
     *
     * @param value 值
     * @return Long 值，转换失败返回 null
     */
    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 转换为 Boolean
     *
     * <p>识别的真值：{@code "true"}、{@code "1"}、{@code "yes"}（大小写不敏感）。
     * <p>识别的假值：{@code "false"}、{@code "0"}、{@code "no"}（大小写不敏感）。
     * <p>其他值（包括无法解析的字符串）返回 {@code null}，以便调用方区分「假值」与「不可解析」。
     *
     * @param value 值
     * @return Boolean 值，不可解析返回 null
     */
    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String str = value.toString().toLowerCase();
        if ("true".equals(str) || "1".equals(str) || "yes".equals(str)) {
            return Boolean.TRUE;
        }
        if ("false".equals(str) || "0".equals(str) || "no".equals(str)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
