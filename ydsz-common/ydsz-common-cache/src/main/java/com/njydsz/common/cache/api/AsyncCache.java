package com.njydsz.common.cache.api;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.njydsz.common.cache.support.AsyncFunction;

/**
 * 异步缓存接口 — 所有操作返回 {@link CompletableFuture}。
 *
 * <p>对标 Caffeine {@code AsyncCache}，适配反应式 / 非阻塞场景：
 *
 * <ul>
 *   <li>查询返回 {@code CompletableFuture<V>}，不阻塞调用线程
 *   <li>写入返回 {@code CompletableFuture<Void>}，异步完成通知
 *   <li>支持异步加载器 {@link AsyncFunction}，加载过程不占用缓存线程
 * </ul>
 *
 * <p>实现类推荐将同步操作包装为 {@code CompletableFuture.completedFuture()} 以保持 API 兼容， 或通过
 * {@link com.njydsz.common.cache.support.CacheThreadPoolManager} 调度实现真正异步。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 26.09.19
 */
public interface AsyncCache<K, V> extends Cache<K, V> {

  /**
   * 异步获取缓存值（如果存在）。
   *
   * <p>返回已完成的 Future（命中 / 未命中均立即完成），不阻塞调用线程。
   *
   * @param key 缓存键
   * @return 包含缓存值的 Future（命中时为值，未命中时为 null）
   */
  CompletableFuture<V> getIfPresentAsync(K key);

  /**
   * 异步获取缓存值，如果不存在则使用异步加载器加载。
   *
   * <p>加载器在后台线程池中异步执行，不阻塞调用线程；同一 key 的并发加载请求共享同一个 Future
   * （防击穿），加载结果对全部等待方可见。
   *
   * @param key 缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
  @Override
  CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader);

  /**
   * 异步批量获取缓存值。
   *
   * <p>缺失键的批量加载由实现类决定（真正的批量查询或逐 key 并行加载）。
   *
   * @param keys 待查询的键集合
   * @return 异步完成的结果映射
   */
  CompletableFuture<Map<K, V>> getAllAsync(Set<K> keys);

  /**
   * 异步放入缓存。
   *
   * @param key 键
   * @param value 值
   * @return 写入完成通知（成功时为正常完成，失败时为异常完成）
   */
  CompletableFuture<Void> putAsync(K key, V value);

  /**
   * 异步移除指定键。
   *
   * @param key 键
   * @return 携带被移除值的 Future（移除的条目不存在时值为 null）
   */
  CompletableFuture<V> removeAsync(K key);

  /**
   * 异步使键失效（等同于异步移除）。
   *
   * @param key 键
   * @return 失效完成通知
   */
  default CompletableFuture<Void> invalidateAsync(K key) {
    return removeAsync(key).thenRun(() -> {});
  }
}
