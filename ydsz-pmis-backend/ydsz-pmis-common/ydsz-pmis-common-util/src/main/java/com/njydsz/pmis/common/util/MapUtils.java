package com.njydsz.pmis.common.util;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Map 工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class MapUtils {

    private MapUtils() {
    }

    /**
     * 判断 Map 是否为空
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Map 是否非空
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 创建 HashMap
     *
     * @param <K> 键类型
     * @param <V> 值类型
     * @return HashMap
     */
    public static <K, V> Map<K, V> newHashMap() {
        return new HashMap<>();
    }

    /**
     * 创建带初始容量的 HashMap
     *
     * @param initialCapacity 初始容量
     * @param <K>             键类型
     * @param <V>             值类型
     * @return HashMap
     */
    public static <K, V> Map<K, V> newHashMap(int initialCapacity) {
        return new HashMap<>(initialCapacity);
    }

    /**
     * 从键值对创建 Map
     *
     * @param kvs 键值对（k1, v1, k2, v2, ...）
     * @param <K> 键类型
     * @param <V> 值类型
     * @return Map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> of(Object... kvs) {
        if (kvs == null || kvs.length == 0) {
            return new HashMap<>();
        }
        if (kvs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even");
        }
        Map<K, V> map = new HashMap<>(kvs.length / 2);
        for (int i = 0; i < kvs.length; i += 2) {
            map.put((K) kvs[i], (V) kvs[i + 1]);
        }
        return map;
    }

    /**
     * Properties 转 Map
     *
     * @param properties Properties
     * @return Map
     */
    public static Map<String, String> fromProperties(Properties properties) {
        Map<String, String> map = new HashMap<>();
        if (properties != null) {
            Enumeration<?> names = properties.propertyNames();
            while (names.hasMoreElements()) {
                String name = (String) names.nextElement();
                map.put(name, properties.getProperty(name));
            }
        }
        return map;
    }

    /**
     * 获取 Map 中指定键的值，不存在返回默认值
     *
     * @param map          Map
     * @param key          键
     * @param defaultValue 默认值
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 值
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (isEmpty(map)) {
            return defaultValue;
        }
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * 合并两个 Map（后者覆盖前者）
     *
     * @param map1 Map1
     * @param map2 Map2
     * @param <K>  键类型
     * @param <V>  值类型
     * @return 合并后的 Map
     */
    public static <K, V> Map<K, V> merge(Map<K, V> map1, Map<K, V> map2) {
        Map<K, V> result = new HashMap<>();
        if (isNotEmpty(map1)) {
            result.putAll(map1);
        }
        if (isNotEmpty(map2)) {
            result.putAll(map2);
        }
        return result;
    }
}
