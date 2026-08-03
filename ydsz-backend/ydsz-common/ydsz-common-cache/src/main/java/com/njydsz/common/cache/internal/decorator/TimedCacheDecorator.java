package com.njydsz.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;

import io.micrometer.core.instrument.Timer;

import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.metrics.CacheMeterBinder;
/**
 * 计时缓存装饰器 — 自动记录 GET/PUT 操作耗时到 Micrometer Timer
 *
 * <p>解决了 {@link com.njydsz.common.cache.metrics.CacheMeterBinder} 中 GET/PUT Timer
 * 空转问题（Timer 创建了但没有任何代码调用 record()）。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * Cache<String, User> cache = new LRUCache<>(1000);
 * CacheMeterBinder binder = new CacheMeterBinder(cache, "user_cache");
 * binder.bindTo(meterRegistry);
 * cache = new TimedCacheDecorator<>(cache, binder);
 * // 后续所有 get/put 操作自动计时
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * 
 * @since 1.0.0
 */
public class TimedCacheDecorator<K, V> implements Cache<K, V> {

  private final Cache<K, V> delegate;
  private final Timer getTimer;
  private final Timer putTimer;

  /**
   * 创建计时缓存装饰器
   *
   * @param delegate 底层缓存
   * @param getTimer GET 操作计时器（null 表示不计时）
   * @param putTimer PUT 操作计时器（null 表示不计时）
   */
  public TimedCacheDecorator(Cache<K, V> delegate, Timer getTimer, Timer putTimer) {
    this.delegate = delegate;
    this.getTimer = getTimer;
    this.putTimer = putTimer;
  }

  /**
   * 创建计时缓存装饰器（从 CacheMeterBinder 获取 Timer）
   *
   * @param delegate 底层缓存
   * @param binder Micrometer 绑定器
   */
  public TimedCacheDecorator(
      Cache<K, V> delegate, CacheMeterBinder binder) {
    this.delegate = delegate;
    this.getTimer = binder.getGetTimer();
    this.putTimer = binder.getPutTimer();
  }

  /**
   * 获取缓存值（不触发加载），并按 GET 计时器记录耗时。
   *
   * <p>计时器为 null 时直接透传，不产生额外开销。耗时统计使用 {@code System.nanoTime()}
   * 在 {@code finally} 中记录，即使底层缓存抛异常也保证统计不遗漏。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    if (getTimer == null) {
      return delegate.getIfPresent(key);
    }
    long start = System.nanoTime();
    try {
      return delegate.getIfPresent(key);
    } finally {
      getTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 获取缓存值（未命中时加载），并按 GET 计时器记录包含加载的总耗时。
   *
   * @param key    缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的新值
   */
  @Override
  public V get(K key, Function<K, V> loader) {
    if (getTimer == null) {
      return delegate.get(key, loader);
    }
    long start = System.nanoTime();
    try {
      return delegate.get(key, loader);
    } finally {
      getTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 异步获取缓存值（未命中时加载），异步完成后记录 GET 耗时。
   *
   * <p>计时通过 {@code whenComplete} 挂载在 Future 上，仅统计从调用到异步完成的全链路耗时。
   *
   * @param key    缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    if (getTimer == null) {
      return delegate.getAsync(key, loader);
    }
    long start = System.nanoTime();
    return delegate
        .getAsync(key, loader)
        .whenComplete(
            (v, ex) -> getTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS));
  }

  /**
   * 写入键值对，并按 PUT 计时器记录耗时。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    if (putTimer == null) {
      delegate.put(key, value);
      return;
    }
    long start = System.nanoTime();
    try {
      delegate.put(key, value);
    } finally {
      putTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 仅当键不存在时写入，并按 PUT 计时器记录耗时。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @return 已存在的旧值；键原本不存在时返回 {@code null}
   */
  @Override
  public V putIfAbsent(K key, V value) {
    if (putTimer == null) {
      return delegate.putIfAbsent(key, value);
    }
    long start = System.nanoTime();
    try {
      return delegate.putIfAbsent(key, value);
    } finally {
      putTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 计算并写入缓存（直接委托，不计时）。
   *
   * @param key             缓存键
   * @param mappingFunction 映射函数
   * @return 计算后的值
   */
  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  /**
   * 基于旧值重新计算映射并写回缓存（直接委托，不计时）。
   *
   * @param key               缓存键
   * @param remappingFunction 重映射函数
   * @return 重映射后的值
   */
  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  /**
   * 合并值与现有值（直接委托，不计时）。
   *
   * @param key               缓存键
   * @param value             待合并的值
   * @param remappingFunction 合并函数
   * @return 合并后的值
   */
  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return delegate.merge(key, value, remappingFunction);
  }

  /**
   * 移除指定键并返回被移除的值（直接委托，不计时）。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    return delegate.remove(key);
  }

  /**
   * 使单个键失效（等价于 {@link #remove}）。
   *
   * @param key 缓存键
   */
  @Override
  public void invalidate(K key) {
    delegate.invalidate(key);
  }

  /**
   * 批量使指定键集合失效。
   *
   * @param keys 待失效的键集合
   */
  @Override
  public void invalidateAll(Collection<K> keys) {
    delegate.invalidateAll(keys);
  }

  /**
   * 使全部键失效（等价于 {@link #clear}）。
   */
  @Override
  public void invalidateAll() {
    delegate.invalidateAll();
  }

  /**
   * 清空缓存（直接委托，不计时）。
   */
  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 批量写入，并按 PUT 计时器记录整体耗时。
   *
   * @param map 待写入的映射
   */
  @Override
  public void putAll(Map<K, V> map) {
    if (putTimer == null) {
      delegate.putAll(map);
      return;
    }
    long start = System.nanoTime();
    try {
      delegate.putAll(map);
    } finally {
      putTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 批量获取指定键的缓存值，并按 GET 计时器记录耗时。
   *
   * @param keys 待获取的键集合
   * @return 命中键值映射；未命中的键不会出现在结果中
   */
  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    if (getTimer == null) {
      return delegate.getAll(keys);
    }
    long start = System.nanoTime();
    try {
      return delegate.getAll(keys);
    } finally {
      getTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 批量移除指定键（直接委托，不计时）。
   *
   * @param keys 待移除的键集合
   */
  @Override
  public void removeAll(Collection<K> keys) {
    delegate.removeAll(keys);
  }

  /**
   * 返回缓存条目数（近似值）。
   *
   * @return 底层缓存条目数
   */
  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  /**
   * 判断缓存是否为空。
   *
   * @return 底层缓存无条目时返回 {@code true}
   */
  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  /**
   * 获取缓存命中率。
   *
   * @return 底层缓存的命中率
   */
  @Override
  public double getHitRate() {
    return delegate.getHitRate();
  }

  /**
   * 获取缓存统计快照。
   *
   * @return 底层缓存的统计对象
   */
  @Override
  public CacheStats getStats() {
    return delegate.getStats();
  }

  /**
   * 获取缓存策略查询接口。
   *
   * @return 底层缓存的策略接口
   */
  @Override
  public CachePolicy policy() {
    return delegate.policy();
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * @param key 缓存键
   * @return 底层缓存存在该键时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    return delegate.containsKey(key);
  }

  /**
   * 返回缓存键集合视图。
   *
   * @return 底层缓存的键集合视图
   */
  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  /**
   * 返回缓存值集合视图。
   *
   * @return 底层缓存的值集合视图
   */
  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  /**
   * 执行缓存维护操作（清理过期条目等）。
   */
  @Override
  public void cleanUp() {
    delegate.cleanUp();
  }

  /**
   * 添加删除监听器。
   *
   * @param listener 删除监听器
   */
  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  /**
   * 遍历缓存键值对。
   *
   * @param action 作用于每个键值对的消费动作
   */
  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    delegate.forEach(action);
  }

  /** 获取底层缓存实例 */
  public Cache<K, V> getDelegate() {
    return delegate;
  }
}
