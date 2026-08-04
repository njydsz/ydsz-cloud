package com.remisoft.common.cache.api;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 支持自动加载的缓存接口
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author remi-team
 * @since 1.0.0
 * 
 */
public interface LoadingCache<K, V> extends Cache<K, V> {

  /**
   * 获取缓存值（自动加载）
   *
   * <p>如果缓存中不存在，则通过配置的 Loader 自动加载
   */
  V get(K key);

  /** 获取缓存值（不触发加载，仅查询） */
  V getIfPresent(K key);

  /** 获取缓存值（不检查异常） */
  V getUnchecked(K key);

  /** 异步获取缓存值 */
  CompletableFuture<V> getAsync(K key);

  /** 批量获取 */
  Map<K, V> getAll(Collection<K> keys);

  /** 批量异步获取 */
  CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys);

  /** 刷新指定键 */
  void refresh(K key);

  @Override
  default V get(K key, Function<K, V> loader) {
    return get(key);
  }
}
