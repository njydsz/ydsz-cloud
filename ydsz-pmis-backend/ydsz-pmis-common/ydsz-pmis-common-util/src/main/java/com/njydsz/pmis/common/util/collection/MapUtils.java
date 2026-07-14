package com.njydsz.pmis.common.util.collection;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Map 工具类 - 增强版
 * 
 * <p>参考互联网大厂（阿里巴巴、Google Guava、Apache Commons Collections、Spring）最佳实践设计，
 * 提供全面、高效、安全的 Map 操作方法。</p>
 * 
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>判空检查：isEmpty、isNotEmpty</li>
 *   <li>安全获取：get、getOrDefault、getString、getInteger、getLong、getDouble、getBoolean</li>
 *   <li>类型转换：asMap、asMapOrEmpty、convertMap</li>
 *   <li>集合创建：newHashMap、newLinkedHashMap、newTreeMap、emptyMap</li>
 *   <li>合并操作：merge、mergeAll、putAll</li>
 *   <li>过滤操作：filter、filterKeys、filterValues</li>
 *   <li>转换操作：transformKeys、transformValues、transformEntries</li>
 *   <li>排序操作：sortByKeys、sortByValues</li>
 *   <li>其他操作：reverse、invert、flatten、deepCopy</li>
 * </ul>
 * 
 * <p><b>相比 Apache Commons Collections 的增强：</b>
 * <ul>
 *   <li>更全面的类型安全获取方法（支持所有基本类型）</li>
 *   <li>提供 Lambda 表达式支持的转换和过滤方法</li>
 *   <li>支持 Map 合并、排序、反转等高级操作</li>
 *   <li>零第三方依赖，纯 JDK 实现</li>
 *   <li>更好的空指针安全防护</li>
 * </ul>
 * 
 * <p><b>使用示例：</b>
 * <pre>
 * // 1. 判空检查
 * if (MapUtils.isEmpty(map)) { ... }
 * 
 * // 2. 安全获取
 * String value = MapUtils.getString(map, "key");
 * Integer num = MapUtils.getInteger(map, "count", 0);
 * 
 * // 3. 类型转换
 * Map&lt;String, Integer&gt; intMap = MapUtils.convertMap(stringMap, Integer::valueOf);
 * 
 * // 4. Map 合并
 * Map&lt;String, Object&gt; merged = MapUtils.merge(map1, map2);
 * 
 * // 5. 过滤操作
 * Map&lt;String, Integer&gt; filtered = MapUtils.filter(map, (k, v) -&gt; v &gt; 10);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class MapUtils {

    private MapUtils() {
        throw new IllegalStateException("Utility class - cannot be instantiated");
    }

    // ==================== 判空方法 ====================

    /**
     * 判断 Map 是否为空
     *
     * @param map Map 对象
     * @return 如果 map 为 null 或 empty 则返回 true
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Map 是否不为空
     *
     * @param map Map 对象
     * @return 如果 map 不为 null 且不为 empty 则返回 true
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    // ==================== 安全获取方法 ====================

    /**
     * 安全获取 Map 中的值
     *
     * @param map Map 对象
     * @param key 键
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 值，如果 map 为空或 key 不存在则返回 null
     */
    public static <K, V> V get(Map<K, V> map, K key) {
        return isEmpty(map) ? null : map.get(key);
    }

    /**
     * 安全获取 Map 中的值，如果不存在则返回默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 值，如果 map 为空或 key 不存在则返回 defaultValue
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        return isEmpty(map) ? defaultValue : map.getOrDefault(key, defaultValue);
    }

    /**
     * 安全获取 Map 中的值，如果不存在或为 null 则返回默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 值，如果 map 为空或 key 不存在或值为 null 则返回 defaultValue
     */
    public static <K, V> V getOrDefaultIfNull(Map<K, V> map, K key, V defaultValue) {
        V value = get(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 String 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return String 值，如果转换失败则返回 null
     */
    public static String getString(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return value != null ? value.toString() : null;
    }

    /**
     * 获取 String 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return String 值，如果转换失败则返回 defaultValue
     */
    public static String getString(Map<?, ?> map, Object key, String defaultValue) {
        String value = getString(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 Integer 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Integer 值，如果转换失败则返回 null
     */
    public static Integer getInteger(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toInteger(value);
    }

    /**
     * 获取 Integer 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return Integer 值，如果转换失败则返回 defaultValue
     */
    public static Integer getInteger(Map<?, ?> map, Object key, Integer defaultValue) {
        Integer value = getInteger(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 int 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return int 值
     */
    public static int getIntValue(Map<?, ?> map, Object key, int defaultValue) {
        Integer value = getInteger(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 Long 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Long 值，如果转换失败则返回 null
     */
    public static Long getLong(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toLong(value);
    }

    /**
     * 获取 Long 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return Long 值，如果转换失败则返回 defaultValue
     */
    public static Long getLong(Map<?, ?> map, Object key, Long defaultValue) {
        Long value = getLong(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 long 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return long 值
     */
    public static long getLongValue(Map<?, ?> map, Object key, long defaultValue) {
        Long value = getLong(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 Double 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Double 值，如果转换失败则返回 null
     */
    public static Double getDouble(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toDouble(value);
    }

    /**
     * 获取 Double 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return Double 值，如果转换失败则返回 defaultValue
     */
    public static Double getDouble(Map<?, ?> map, Object key, Double defaultValue) {
        Double value = getDouble(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 double 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return double 值
     */
    public static double getDoubleValue(Map<?, ?> map, Object key, double defaultValue) {
        Double value = getDouble(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 Float 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Float 值，如果转换失败则返回 null
     */
    public static Float getFloat(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toFloat(value);
    }

    /**
     * 获取 Float 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return Float 值，如果转换失败则返回 defaultValue
     */
    public static Float getFloat(Map<?, ?> map, Object key, Float defaultValue) {
        Float value = getFloat(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 Boolean 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Boolean 值，如果转换失败则返回 null
     */
    public static Boolean getBoolean(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return toBoolean(value);
    }

    /**
     * 获取 Boolean 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return Boolean 值，如果转换失败则返回 defaultValue
     */
    public static Boolean getBoolean(Map<?, ?> map, Object key, Boolean defaultValue) {
        Boolean value = getBoolean(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 boolean 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return boolean 值
     */
    public static boolean getBooleanValue(Map<?, ?> map, Object key, boolean defaultValue) {
        Boolean value = getBoolean(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 Map 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return Map 值，如果转换失败则返回 null
     */
    public static Map<?, ?> getMap(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    /**
     * 获取 Map 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return Map 值，如果转换失败则返回 defaultValue
     */
    public static Map<?, ?> getMap(Map<?, ?> map, Object key, Map<?, ?> defaultValue) {
        Map<?, ?> value = getMap(map, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取 List 类型值
     *
     * @param map Map 对象
     * @param key 键
     * @return List 值，如果转换失败则返回 null
     */
    public static List<?> getList(Map<?, ?> map, Object key) {
        Object value = map != null ? map.get(key) : null;
        return value instanceof List ? (List<?>) value : null;
    }

    /**
     * 获取 List 类型值，带默认值
     *
     * @param map Map 对象
     * @param key 键
     * @param defaultValue 默认值
     * @return List 值，如果转换失败则返回 defaultValue
     */
    public static List<?> getList(Map<?, ?> map, Object key, List<?> defaultValue) {
        List<?> value = getList(map, key);
        return value != null ? value : defaultValue;
    }

    // ==================== Object 转 Map 方法 ====================

    /**
     * 安全转换 Object 为 Map
     *
     * @param obj 要转换的对象
     * @param <K> Map 键类型
     * @param <V> Map 值类型
     * @return 转换后的 Map，如果转换失败则返回 null
     */
    
    public static <K, V> Map<K, V> asMap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return castToMap(obj);
        }
        return null;
    }

    /**
     * 安全转换 Object 为 Map，如果转换失败则返回空 Map
     *
     * @param obj 要转换的对象
     * @param <K> Map 键类型
     * @param <V> Map 值类型
     * @return 转换后的 Map，如果转换失败则返回空 Map
     */
    public static <K, V> Map<K, V> asMapOrEmpty(Object obj) {
        Map<K, V> map = asMap(obj);
        return map != null ? map : newHashMap();
    }

    /**
     * 安全转换 Object 为 Map，如果转换失败则返回默认值
     *
     * @param obj 要转换的对象
     * @param defaultMap 默认 Map
     * @param <K> Map 键类型
     * @param <V> Map 值类型
     * @return 转换后的 Map，如果转换失败则返回 defaultMap
     */
    public static <K, V> Map<K, V> asMapOrDefault(Object obj, Map<K, V> defaultMap) {
        Map<K, V> map = asMap(obj);
        return map != null ? map : defaultMap;
    }

    /**
     * 检查 Object 是否为 Map 类型
     *
     * @param obj 要检查的对象
     * @return 如果是 Map 类型则返回 true
     */
    public static boolean isMap(Object obj) {
        return obj instanceof Map;
    }

    // ==================== 集合创建方法 ====================

    /**
     * 创建新的 HashMap
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 HashMap
     */
    public static <K, V> Map<K, V> newHashMap() {
        return new HashMap<>();
    }

    /**
     * 创建新的 HashMap，带初始容量
     *
     * @param initialCapacity 初始容量
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 HashMap
     */
    public static <K, V> Map<K, V> newHashMap(int initialCapacity) {
        return new HashMap<>(initialCapacity);
    }

    /**
     * 创建新的 LinkedHashMap
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 LinkedHashMap
     */
    public static <K, V> Map<K, V> newLinkedHashMap() {
        return new LinkedHashMap<>();
    }

    /**
     * 创建新的 TreeMap
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 TreeMap
     */
    public static <K extends Comparable<? super K>, V> Map<K, V> newTreeMap() {
        return new TreeMap<>();
    }

    /**
     * 创建新的 TreeMap，带比较器
     *
     * @param comparator 比较器
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 新的 TreeMap
     */
    public static <K, V> Map<K, V> newTreeMap(Comparator<? super K> comparator) {
        return new TreeMap<>(comparator);
    }

    /**
     * 创建空的不可变 Map
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 空的不可变 Map
     */
    public static <K, V> Map<K, V> emptyMap() {
        return Collections.emptyMap();
    }

    /**
     * 创建单元素的不可变 Map
     *
     * @param key 键
     * @param value 值
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 不可变 Map
     */
    public static <K, V> Map<K, V> of(K key, V value) {
        return Collections.singletonMap(key, value);
    }

    /**
     * 创建双元素的不可变 Map
     *
     * @param k1 键 1
     * @param v1 值 1
     * @param k2 键 2
     * @param v2 值 2
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 不可变 Map
     */
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return Collections.unmodifiableMap(map);
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
     * 转换为 Double
     *
     * @param value 值
     * @return Double 值，转换失败返回 null
     */
    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 转换为 Float
     *
     * @param value 值
     * @return Float 值，转换失败返回 null
     */
    private static Float toFloat(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        try {
            return Float.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 转换为 Boolean
     *
     * @param value 值
     * @return Boolean 值，转换失败返回 null
     */
    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String str = value.toString().toLowerCase();
        return "true".equals(str) || "1".equals(str) || "yes".equals(str);
    }

    // ==================== Map 转换方法 ====================

    /**
     * 转换 Map 的键类型
     *
     * @param map 源 Map
     * @param keyMapper 键转换函数
     * @param <K1> 源键类型
     * @param <K2> 目标键类型
     * @param <V> 值类型
     * @return 转换后的 Map
     */
    public static <K1, K2, V> Map<K2, V> transformKeys(Map<K1, V> map, Function<? super K1, ? extends K2> keyMapper) {
        if (isEmpty(map) || keyMapper == null) {
            return newHashMap();
        }
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> keyMapper.apply(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    /**
     * 转换 Map 的值类型
     *
     * @param map 源 Map
     * @param valueMapper 值转换函数
     * @param <K> 键类型
     * @param <V1> 源值类型
     * @param <V2> 目标值类型
     * @return 转换后的 Map
     */
    public static <K, V1, V2> Map<K, V2> transformValues(Map<K, V1> map, Function<? super V1, ? extends V2> valueMapper) {
        if (isEmpty(map) || valueMapper == null) {
            return newHashMap();
        }
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> valueMapper.apply(entry.getValue())
                ));
    }

    /**
     * 转换 Map 的键和值
     *
     * @param map 源 Map
     * @param keyMapper 键转换函数
     * @param valueMapper 值转换函数
     * @param <K1> 源键类型
     * @param <K2> 目标键类型
     * @param <V1> 源值类型
     * @param <V2> 目标值类型
     * @return 转换后的 Map
     */
    public static <K1, K2, V1, V2> Map<K2, V2> transformEntries(
            Map<K1, V1> map,
            Function<? super K1, ? extends K2> keyMapper,
            Function<? super V1, ? extends V2> valueMapper) {
        if (isEmpty(map) || keyMapper == null || valueMapper == null) {
            return newHashMap();
        }
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> keyMapper.apply(entry.getKey()),
                        entry -> valueMapper.apply(entry.getValue())
                ));
    }

    /**
     * 转换 Map 的值类型（带类型转换）
     *
     * @param map 源 Map
     * @param converter 值转换器
     * @param <K> 键类型
     * @param <V1> 源值类型
     * @param <V2> 目标值类型
     * @return 转换后的 Map
     */
    public static <K, V1, V2> Map<K, V2> convertMap(Map<K, V1> map, Function<? super V1, ? extends V2> converter) {
        return transformValues(map, converter);
    }

    // ==================== Map 过滤方法 ====================

    /**
     * 过滤 Map 条目
     *
     * @param map 源 Map
     * @param predicate 过滤条件
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 过滤后的 Map
     */
    public static <K, V> Map<K, V> filter(Map<K, V> map, Predicate<Map.Entry<K, V>> predicate) {
        if (isEmpty(map) || predicate == null) {
            return newHashMap();
        }
        return map.entrySet().stream()
                .filter(predicate)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * 根据键过滤 Map
     *
     * @param map 源 Map
     * @param keyPredicate 键过滤条件
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 过滤后的 Map
     */
    public static <K, V> Map<K, V> filterKeys(Map<K, V> map, Predicate<? super K> keyPredicate) {
        if (isEmpty(map) || keyPredicate == null) {
            return newHashMap();
        }
        return map.entrySet().stream()
                .filter(entry -> keyPredicate.test(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * 根据值过滤 Map
     *
     * @param map 源 Map
     * @param valuePredicate 值过滤条件
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 过滤后的 Map
     */
    public static <K, V> Map<K, V> filterValues(Map<K, V> map, Predicate<? super V> valuePredicate) {
        if (isEmpty(map) || valuePredicate == null) {
            return newHashMap();
        }
        return map.entrySet().stream()
                .filter(entry -> valuePredicate.test(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    // ==================== Map 合并方法 ====================

    /**
     * 合并两个 Map
     *
     * @param map1 第一个 Map
     * @param map2 第二个 Map
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 合并后的 Map
     */
    public static <K, V> Map<K, V> merge(Map<K, V> map1, Map<K, V> map2) {
        Map<K, V> result = newHashMap();
        if (isNotEmpty(map1)) {
            result.putAll(map1);
        }
        if (isNotEmpty(map2)) {
            result.putAll(map2);
        }
        return result;
    }

    /**
     * 合并多个 Map
     *
     * @param maps 多个 Map
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 合并后的 Map
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mergeAll(Map<K, V>... maps) {
        Map<K, V> result = newHashMap();
        if (maps != null) {
            for (Map<K, V> map : maps) {
                if (isNotEmpty(map)) {
                    result.putAll(map);
                }
            }
        }
        return result;
    }

    /**
     * 批量赋值
     *
     * @param map 目标 Map
     * @param entries 键值对数组（按顺序：key1, value1, key2, value2, ...）
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 赋值后的 Map
     */
    
    public static <K, V> Map<K, V> putAll(Map<K, V> map, Object... entries) {
        if (map == null || entries == null || entries.length % 2 != 0) {
            return map;
        }
        for (int i = 0; i < entries.length; i += 2) {
            map.put(castKey(entries[i]), castValue(entries[i + 1]));
        }
        return map;
    }

    /** 内部辅助方法：安全转换 Object 为泛型 Map */
    private static <K, V> Map<K, V> castToMap(Object obj) {
        return (Map<K, V>) obj;
    }

    /** 内部辅助方法：安全转换 Object 为泛型 Key */
    private static <K> K castKey(Object obj) {
        return (K) obj;
    }

    /** 内部辅助方法：安全转换 Object 为泛型 Value */
    private static <V> V castValue(Object obj) {
        return (V) obj;
    }

    /**
     * 合并两个 Map，冲突时使用 mergeFunction 处理
     *
     * @param map1 第一个 Map
     * @param map2 第二个 Map
     * @param mergeFunction 合并函数
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 合并后的 Map
     */
    public static <K, V> Map<K, V> merge(Map<K, V> map1, Map<K, V> map2,
                                          BiFunction<? super V, ? super V, ? extends V> mergeFunction) {
        if (isEmpty(map1)) {
            return isEmpty(map2) ? newHashMap() : new HashMap<>(map2);
        }
        if (isEmpty(map2)) {
            return new HashMap<>(map1);
        }

        Map<K, V> result = new HashMap<>(map1);
        map2.forEach((key, value) -> result.merge(key, value, mergeFunction));
        return result;
    }

    // ==================== Map 排序方法 ====================

    /**
     * 按键排序 Map
     *
     * @param map 源 Map
     * @param <K> 键类型（必须实现 Comparable）
     * @param <V> 值类型
     * @return 排序后的 LinkedHashMap
     */
    public static <K extends Comparable<? super K>, V> Map<K, V> sortByKeys(Map<K, V> map) {
        if (isEmpty(map)) {
            return newLinkedHashMap();
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 按键排序 Map（自定义比较器）
     *
     * @param map 源 Map
     * @param comparator 比较器
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 排序后的 LinkedHashMap
     */
    public static <K, V> Map<K, V> sortByKeys(Map<K, V> map, Comparator<? super K> comparator) {
        if (isEmpty(map) || comparator == null) {
            return newLinkedHashMap();
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(comparator))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 按值排序 Map
     *
     * @param map 源 Map
     * @param <K> 键类型
     * @param <V> 值类型（必须实现 Comparable）
     * @return 排序后的 LinkedHashMap
     */
    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValues(Map<K, V> map) {
        if (isEmpty(map)) {
            return newLinkedHashMap();
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 按值排序 Map（自定义比较器）
     *
     * @param map 源 Map
     * @param comparator 比较器
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 排序后的 LinkedHashMap
     */
    public static <K, V> Map<K, V> sortByValues(Map<K, V> map, Comparator<? super V> comparator) {
        if (isEmpty(map) || comparator == null) {
            return newLinkedHashMap();
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(comparator))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
    }

    // ==================== 其他高级操作 ====================

    /**
     * 反转 Map（键值互换）
     *
     * @param map 源 Map
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 反转后的 Map
     */
    public static <K, V> Map<V, K> invert(Map<K, V> map) {
        if (isEmpty(map)) {
            return newHashMap();
        }
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue,
                        Map.Entry::getKey
                ));
    }

    /**
     * 深度复制 Map
     *
     * @param map 源 Map
     * @param <K> 键类型
     * @param <V> 值类型
     * @return 复制后的 Map
     */
    
    public static <K, V> Map<K, V> deepCopy(Map<K, V> map) {
        if (isEmpty(map)) {
            return newHashMap();
        }
        return castToMap(map.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() != null && entry.getValue() instanceof Map
                                ? deepCopy((Map<?, ?>) entry.getValue())
                                : entry.getValue()
                )));
    }

    /**
     * 扁平化嵌套 Map
     *
     * @param map 嵌套 Map
     * @param separator 分隔符
     * @return 扁平化后的 Map
     */
    public static Map<String, Object> flatten(Map<?, ?> map, String separator) {
        Map<String, Object> result = newHashMap();
        if (isEmpty(map)) {
            return result;
        }
        flatten(map, "", separator != null ? separator : ".", result);
        return result;
    }

    private static void flatten(Map<?, ?> map, String prefix, String separator, Map<String, Object> result) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? String.valueOf(entry.getKey())
                    : prefix + separator + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten((Map<?, ?>) value, key, separator, result);
            } else {
                result.put(key, value);
            }
        }
    }

    /**
     * 获取 Map 的大小，如果为 null 则返回 0
     *
     * @param map Map 对象
     * @return Map 的大小
     */
    public static int size(Map<?, ?> map) {
        return map == null ? 0 : map.size();
    }

    /**
     * 判断 Map 是否包含任意一个给定的键
     *
     * @param map Map 对象
     * @param keys 键数组
     * @param <K> 键类型
     * @return 如果包含任意一个键则返回 true
     */
    @SafeVarargs
    public static <K> boolean containsAny(Map<K, ?> map, K... keys) {
        if (isEmpty(map) || keys == null || keys.length == 0) {
            return false;
        }
        for (K key : keys) {
            if (map.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 Map 是否包含所有给定的键
     *
     * @param map Map 对象
     * @param keys 键数组
     * @param <K> 键类型
     * @return 如果包含所有键则返回 true
     */
    @SafeVarargs
    public static <K> boolean containsAll(Map<K, ?> map, K... keys) {
        if (isEmpty(map) || keys == null || keys.length == 0) {
            return false;
        }
        for (K key : keys) {
            if (!map.containsKey(key)) {
                return false;
            }
        }
        return true;
    }
}
