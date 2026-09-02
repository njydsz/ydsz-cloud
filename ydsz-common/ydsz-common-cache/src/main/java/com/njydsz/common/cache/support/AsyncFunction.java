package com.njydsz.common.cache.support;

import java.util.concurrent.CompletableFuture;

/**
 * 异步函数接口
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 26.09.01
 */
@FunctionalInterface
public interface AsyncFunction<K, V> {
  /**
   * 异步应用函数
   *
   * @param key 键
   * @return 值的 CompletableFuture
   */
  CompletableFuture<V> apply(K key);
}
