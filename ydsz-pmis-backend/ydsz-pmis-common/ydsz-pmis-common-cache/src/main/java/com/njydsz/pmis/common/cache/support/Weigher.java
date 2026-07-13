package com.njydsz.pmis.common.cache.support;

/**
 * 权重计算器接口
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@FunctionalInterface
public interface Weigher<K, V> {
    /**
     * 计算键值对的权重
     *
     * @param key 键
     * @param value 值
     * @return 权重值
     */
    long weigh(K key, V value);
}
