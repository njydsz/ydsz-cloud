package com.njydsz.common.cache.api;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.njydsz.common.cache.support.AsyncFunction;

/**
 * 异步缓存接口 — 所有操作返回 CompletableFuture
 *
 * <p>参考 Caffeine 的 AsyncCache 设计，提供完全异步的缓存操作能力。 与 {@link Cache} 的同步操作不同，AsyncCache 的所有操作都是非阻塞的，
 * 适合在响应式编程和异步 IO 场景中使用。
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>异步查询：getIfPresent 返回 CompletableFuture
 *   <li>异步加载：get 方法支持异步加载器，自动防击穿
 *   <li>异步批量：getAll 支持批量异步加载
 *   <li>异步写入：put 返回 CompletableFuture
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * 
 */
public interface AsyncCache<K, V> {

  /**
   * 异步获取缓存值（如果存在）
   *
   * @param key 缓存键
   * @return 包含缓存值的 CompletableFuture，不存在时返回包含 null 的 Future
   */
  CompletableFuture<V> getIfPresent(K key);

  /**
   * 异步获取缓存值，如果不存在则使用加载器加载
   *
   * <p>对同一个 key 的并发请求会共享同一个 Future，实现防击穿。
   *
   * @param key 缓存键
   * @param loader 异步加载器
   * @return 包含缓存值的 CompletableFuture
   */
  CompletableFuture<V> get(K key, AsyncFunction<K, V> loader);

  /**
   * 异步批量获取缓存值
   *
   * @param keys 缓存键集合
   * @param loader 批量异步加载器
   * @return 包含结果 Map 的 CompletableFuture
   */
  CompletableFuture<Map<K, V>> getAll(
      Collection<K> keys, AsyncFunction<Collection<K>, Map<K, V>> loader);

  /**
   * 异步放入缓存
   *
   * @param key 缓存键
   * @param value 缓存值
   * @return 表示操作完成的 CompletableFuture
   */
  CompletableFuture<Void> put(K key, V value);

  /**
   * 获取底层同步缓存
   *
   * @return 同步缓存实例
   */
  Cache<K, V> synchronous();
}
