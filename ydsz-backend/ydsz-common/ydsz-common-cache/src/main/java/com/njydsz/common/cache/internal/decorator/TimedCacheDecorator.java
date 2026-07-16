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
 * 
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
      Cache<K, V> delegate, com.njydsz.common.cache.metrics.CacheMeterBinder binder) {
    this.delegate = delegate;
    this.getTimer = binder.getGetTimer();
    this.putTimer = binder.getPutTimer();
  }

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

  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    return delegate.compute(key, remappingFunction);
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    return delegate.merge(key, value, remappingFunction);
  }

  @Override
  public V remove(K key) {
    return delegate.remove(key);
  }

  @Override
  public void invalidate(K key) {
    delegate.invalidate(key);
  }

  @Override
  public void invalidateAll(Collection<K> keys) {
    delegate.invalidateAll(keys);
  }

  @Override
  public void invalidateAll() {
    delegate.invalidateAll();
  }

  @Override
  public void clear() {
    delegate.clear();
  }

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

  @Override
  public void removeAll(Collection<K> keys) {
    delegate.removeAll(keys);
  }

  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public double getHitRate() {
    return delegate.getHitRate();
  }

  @Override
  public CacheStats getStats() {
    return delegate.getStats();
  }

  @Override
  public com.njydsz.common.cache.api.CachePolicy policy() {
    return delegate.policy();
  }

  @Override
  public boolean containsKey(K key) {
    return delegate.containsKey(key);
  }

  @Override
  public Set<K> keySet() {
    return delegate.keySet();
  }

  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  @Override
  public void cleanUp() {
    delegate.cleanUp();
  }

  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    delegate.forEach(action);
  }

  /** 获取底层缓存实例 */
  public Cache<K, V> getDelegate() {
    return delegate;
  }
}
