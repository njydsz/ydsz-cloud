package com.remisoft.common.cache.api;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.remisoft.common.cache.support.AsyncFunction;

import java.util.Collections;
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
 *   <li>主动刷新：refresh / refreshAll 强制重新加载，绕过缓存命中检查
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author remi-team
 * 
 * @since 1.0.0
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
   * 主动刷新单个缓存键 — 强制重新加载，绕过缓存命中检查
   *
   * <p>与 {@link #get(Object, AsyncFunction)} 的区别：
   *
   * <ul>
   *   <li>{@code get} 在缓存命中时直接返回旧值，不触发加载
   *   <li>{@code refresh} 总是调用 loader 重新加载，加载成功后用新值覆盖缓存
   * </ul>
   *
   * <p>刷新失败时的行为：
   *
   * <ul>
   *   <li>加载器抛异常：返回异常完成的 Future，<b>保留缓存中的旧值</b>（避免后台刷新失败导致缓存被清空）
   *   <li>加载器返回 null：返回包含 null 的 Future，并从缓存中移除该键
   *   <li>加载器返回非 null：返回包含新值的 Future，并更新缓存
   * </ul>
   *
   * <p>对同一 key 的并发刷新请求会共享同一个 Future，实现刷新防击穿。
   *
   * @param key 缓存键
   * @param loader 异步加载器（必须非 null）
   * @return 包含新值的 CompletableFuture；加载失败时返回异常完成的 Future
   * @since 1.0.0
   */
  CompletableFuture<V> refresh(K key, AsyncFunction<K, V> loader);

  /**
   * 主动批量刷新缓存键 — 强制重新加载多个键
   *
   * <p>底层调用批量加载器一次性加载所有未命中键，避免逐键触发加载。
   * 加载失败的键会保留旧值（不删除缓存），加载成功的键用新值覆盖。
   *
   * @param keys 需要刷新的键集合（必须非 null 且非空）
   * @param loader 批量异步加载器（必须非 null）
   * @return 包含刷新结果 Map 的 CompletableFuture（仅包含加载成功的键值对）
   * @since 1.0.0
   */
  CompletableFuture<Map<K, V>> refreshAll(
      Collection<K> keys, AsyncFunction<Collection<K>, Map<K, V>> loader);

  /**
   * 刷新缓存中的所有键
   *
   * <p>等价于 {@code refreshAll(synchronous().keySet(), loader)}。 当缓存为空时返回空 Map，不调用 loader。
   *
   * <p><b>注意</b>：此方法会遍历底层缓存的 {@link Cache#keySet()}，对于大容量缓存可能产生较高开销。
   * 建议仅在缓存键数量可控（如配置类缓存）或离线场景使用。
   *
   * @param loader 批量异步加载器（必须非 null）
   * @return 包含刷新结果 Map 的 CompletableFuture
   * @since 1.0.0
   */
  default CompletableFuture<Map<K, V>> refreshAll(AsyncFunction<Collection<K>, Map<K, V>> loader) {
    Collection<K> allKeys = synchronous().keySet();
    if (allKeys == null || allKeys.isEmpty()) {
      return CompletableFuture.completedFuture(Collections.emptyMap());
    }
    return refreshAll(allKeys, loader);
  }

  /**
   * 获取底层同步缓存
   *
   * @return 同步缓存实例
   */
  Cache<K, V> synchronous();
}
