package com.njydsz.pmis.common.cache.internal.decorator;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.api.CachePolicy;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;
import com.njydsz.pmis.common.cache.support.CacheLoader;

/**
 * SWR (Stale-While-Revalidate) 缓存装饰器
 *
 * <p>参考 HTTP Cache-Control 的 stale-while-revalidate 语义： 当缓存条目过期后，
 * 先返回旧值（stale），同时异步触发重新加载（revalidate）。 新值加载完成后替换旧值。
 *
 * <p>这种模式在以下场景中特别有效：
 *
 * <ul>
 *   <li>对延迟敏感但可以容忍短暂数据不一致的读场景
 *   <li>高并发下避免过期时刻的缓存击穿
 *   <li>后端数据源加载较慢但用户可以接受旧数据
 * </ul>
 *
 * <p>工作流程：
 *
 * <ol>
 *   <li>读取时检查条目是否在 freshPeriod 内（新鲜期）→ 直接返回
 *   <li>如果超过 freshPeriod 但在 stalePeriod 内（陈旧期）→ 返回旧值 + 异步刷新
 *   <li>如果超过 stalePeriod → 同步加载新值
 * </ol>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-pmis-team
 * 
 */
public class SwrCacheDecorator<K, V> implements Cache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(SwrCacheDecorator.class);

  private final Cache<K, V> delegate;
  private final CacheLoader<K, V> loader;
  private final long freshPeriodNanos;
  private final long stalePeriodNanos;
  private final Executor executor;

  /** 记录每个 key 的写入时间戳（用于判断 fresh/stale） */
  private final ConcurrentHashMap<K, Long> writeTimestamps = new ConcurrentHashMap<>();

  /** 正在刷新的 key 集合（防止重复刷新） */
  private final ConcurrentHashMap<K, CompletableFuture<Void>> refreshingKeys =
      new ConcurrentHashMap<>();

  /** SWR 统计：陈旧返回次数 */
  private final LongAdder staleReturnCount = new LongAdder();

  /** SWR 统计：异步刷新触发次数 */
  private final LongAdder refreshTriggerCount = new LongAdder();

  /**
   * 创建 SWR 缓存装饰器
   *
   * @param delegate 底层缓存
   * @param loader 数据加载器
   * @param freshPeriod 新鲜期（在此期间直接返回缓存值）
   * @param stalePeriod 陈旧期（在此期间返回旧值+异步刷新）
   * @param timeUnit 时间单位
   * @param executor 异步刷新执行器（null 使用 ForkJoinPool）
   */
  public SwrCacheDecorator(
      Cache<K, V> delegate,
      CacheLoader<K, V> loader,
      long freshPeriod,
      long stalePeriod,
      TimeUnit timeUnit,
      Executor executor) {
    this.delegate = delegate;
    this.loader = loader;
    this.freshPeriodNanos = timeUnit.toNanos(freshPeriod);
    this.stalePeriodNanos = timeUnit.toNanos(stalePeriod);
    this.executor = executor != null ? executor : ForkJoinPool.commonPool();
  }

  @Override
  public V getIfPresent(K key) {
    V value = delegate.getIfPresent(key);
    if (value == null) {
      return null;
    }

    Long writeTime = writeTimestamps.get(key);
    if (writeTime == null) {
      return value;
    }

    long elapsed = System.nanoTime() - writeTime;
    if (elapsed < freshPeriodNanos) {
      // 新鲜期内，直接返回
      return value;
    } else if (elapsed < freshPeriodNanos + stalePeriodNanos) {
      // 陈旧期内，返回旧值 + 异步刷新
      staleReturnCount.increment();
      triggerAsyncRefresh(key);
      return value;
    } else {
      // 超过陈旧期，返回 null（让调用者同步加载）
      return null;
    }
  }

  @Override
  public V get(K key, Function<K, V> loaderFn) {
    V value = getIfPresent(key);
    if (value != null) {
      return value;
    }
    // 同步加载
    value = loaderFn.apply(key);
    if (value != null) {
      put(key, value);
    }
    return value;
  }

  /** 触发异步刷新（防重复） */
  private void triggerAsyncRefresh(K key) {
    refreshingKeys.computeIfAbsent(
        key,
        k -> {
          refreshTriggerCount.increment();
          return CompletableFuture.runAsync(
                  () -> {
                    try {
                      V newValue = loader.load(k);
                      if (newValue != null) {
                        delegate.put(k, newValue);
                        writeTimestamps.put(k, System.nanoTime());
                      }
                    } catch (Exception e) {
                      log.warn("SWR 异步刷新失败: key={}", k, e);
                    }
                  },
                  executor)
              .whenComplete((v, ex) -> refreshingKeys.remove(k));
        });
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
    writeTimestamps.put(key, System.nanoTime());
  }

  @Override
  public V remove(K key) {
    writeTimestamps.remove(key);
    return delegate.remove(key);
  }

  @Override
  public void clear() {
    writeTimestamps.clear();
    refreshingKeys.clear();
    delegate.clear();
  }

  /** 获取陈旧返回次数 */
  public long getStaleReturnCount() {
    return staleReturnCount.sum();
  }

  /** 获取刷新触发次数 */
  public long getRefreshTriggerCount() {
    return refreshTriggerCount.sum();
  }

  // === 以下方法直接委托给底层缓存 ===

  @Override
  public V putIfAbsent(K key, V value) {
    V existing = delegate.putIfAbsent(key, value);
    if (existing == null) {
      writeTimestamps.put(key, System.nanoTime());
    }
    return existing;
  }

  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    V result = delegate.compute(key, remappingFunction);
    if (result != null) {
      writeTimestamps.put(key, System.nanoTime());
    }
    return result;
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    V result = delegate.merge(key, value, remappingFunction);
    if (result != null) {
      writeTimestamps.put(key, System.nanoTime());
    }
    return result;
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader);
  }

  @Override
  public void putAll(Map<K, V> map) {
    delegate.putAll(map);
    long now = System.nanoTime();
    map.forEach((k, v) -> writeTimestamps.put(k, now));
  }

  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    return delegate.getAll(keys);
  }

  @Override
  public void removeAll(Collection<K> keys) {
    keys.forEach(writeTimestamps::remove);
    delegate.removeAll(keys);
  }

  @Override
  public void invalidate(K key) {
    remove(key);
  }

  @Override
  public void invalidateAll(Collection<K> keys) {
    removeAll(keys);
  }

  @Override
  public void invalidateAll() {
    clear();
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
  public CachePolicy policy() {
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
}
