package com.njydsz.common.cache.api;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 支持自动加载的缓存接口
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LoadingCache<K, V> extends Cache<K, V> {

  /**
   * 获取缓存值（自动加载）。
   *
   * <p>如果缓存中不存在，则通过配置的 Loader 自动加载。
   *
   * @param key 键
   * @return 键对应的值；未命中时由 Loader 加载后返回，加载失败或 Loader 返回 {@code null} 时抛出受检异常或返回 {@code null}，
   *     取决于具体实现
   */
  V get(K key);

  /**
   * 获取缓存值（不触发加载，仅查询）
   *
   * @param key 键
   * @return 键对应的值；未命中且未加载过时为 {@code null}，本方法绝不触发 Loader，因此不会引入加载副作用
   */
  V getIfPresent(K key);

  /**
   * 获取缓存值（不检查异常）
   *
   * @param key 键
   * @return 键对应的值；未命中时自动加载，加载过程中抛出的受检异常会被包装为运行时异常抛出
   */
  V getUnchecked(K key);

  /**
   * 异步获取缓存值
   *
   * @param key 键
   * @return 异步结果，不会为 {@code null}；加载失败时该 {@code Future} 以异常完成（{@code completedExceptionally}）
   */
  CompletableFuture<V> getAsync(K key);

  /**
   * 批量获取
   *
   * @param keys keys 参数
   * @return 键到值的映射，不会为 {@code null}；已全部命中时大小等于 {@code keys} 去重后的数量，
   *     加载失败的键不会出现在结果中
   */
  Map<K, V> getAll(Collection<K> keys);

  /**
   * 批量异步获取
   *
   * @param keys keys 参数
   * @return 异步结果，不会为 {@code null}；个别键加载失败时整个 {@code Future} 以异常完成，不做部分成功降级
   */
  CompletableFuture<Map<K, V>> getAllAsync(Collection<K> keys);

  /**
   * 刷新指定键
   *
   * @param key 键
   */
  void refresh(K key);

  @Override
  default V get(K key, Function<K, V> loader) {
    return get(key);
  }
}
