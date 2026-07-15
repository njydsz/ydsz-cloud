package com.njydsz.pmis.common.cache.listener;

/**
 * 缓存删除监听器
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @since 1.0.0
 * 
 */
@FunctionalInterface
public interface RemovalListener<K, V> {
  /**
   * 当缓存项被移除时调用
   *
   * @param key 键
   * @param value 值
   * @param cause 删除原因
   */
  void onRemoval(K key, V value, RemovalCause cause);
}
