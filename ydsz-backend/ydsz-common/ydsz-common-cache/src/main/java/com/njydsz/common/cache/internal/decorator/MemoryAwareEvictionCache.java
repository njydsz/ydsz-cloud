package com.njydsz.common.cache.internal.decorator;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheThreadPoolManager;

/**
 * 内存感知淘汰装饰器 — 根据 JVM 堆内存使用率自动淘汰缓存条目
 *
 * <p>当 JVM 堆内存使用率超过阈值时，自动清除部分或全部缓存条目， 防止 OOM。
 *
 * <p>淘汰策略：
 *
 * <ol>
 *   <li>内存使用率 > warnThreshold（默认 75%）：记录告警日志
 *   <li>内存使用率 > evictThreshold（默认 85%）：清除 50% 缓存条目
 *   <li>内存使用率 > criticalThreshold（默认 95%）：清除全部缓存条目
 * </ol>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * 
 */
public class MemoryAwareEvictionCache<K, V> implements Cache<K, V>, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(MemoryAwareEvictionCache.class);

  private static final MemoryMXBean MEMORY_MXBEAN = ManagementFactory.getMemoryMXBean();

  private final Cache<K, V> delegate;
  private final double warnThreshold;
  private final double evictThreshold;
  private final double criticalThreshold;
  private final ScheduledExecutorService monitor;

  private final AtomicLong evictCount = new AtomicLong(0);
  private final AtomicLong criticalEvictCount = new AtomicLong(0);

  /**
   * 创建内存感知淘汰缓存
   *
   * @param delegate 底层缓存
   * @param warnThreshold 告警阈值（0-1）
   * @param evictThreshold 淘汰阈值（0-1）
   * @param criticalThreshold 临界清除阈值（0-1）
   * @param checkIntervalSeconds 检查间隔（秒）
   */
  public MemoryAwareEvictionCache(
      Cache<K, V> delegate,
      double warnThreshold,
      double evictThreshold,
      double criticalThreshold,
      long checkIntervalSeconds) {
    this.delegate = delegate;
    this.warnThreshold = warnThreshold;
    this.evictThreshold = evictThreshold;
    this.criticalThreshold = criticalThreshold;
    this.monitor =
        CacheThreadPoolManager.getInstance().getOrCreateScheduledPool("memory-aware-monitor", 1);
    this.monitor.scheduleAtFixedRate(
        this::checkMemory, checkIntervalSeconds, checkIntervalSeconds, TimeUnit.SECONDS);
    log.info(
        "MemoryAwareEvictionCache 已创建, warn={}, evict={}, critical={}",
        warnThreshold,
        evictThreshold,
        criticalThreshold);
  }

  /** 检查内存使用率并执行淘汰 */
  private void checkMemory() {
    try {
      MemoryUsage heapUsage = MEMORY_MXBEAN.getHeapMemoryUsage();
      double usedRatio = (double) heapUsage.getUsed() / heapUsage.getMax();

      if (usedRatio >= criticalThreshold) {
        log.warn("内存使用率达到临界值: {}%, 清除全部缓存条目", String.format("%.2f", usedRatio * 100));
        delegate.clear();
        criticalEvictCount.incrementAndGet();
      } else if (usedRatio >= evictThreshold) {
        log.warn("内存使用率过高: {}%, 清除 50% 缓存条目", String.format("%.2f", usedRatio * 100));
        evictHalfEntries();
        evictCount.incrementAndGet();
      } else if (usedRatio >= warnThreshold) {
        log.info("内存使用率告警: {}%", String.format("%.2f", usedRatio * 100));
      }
    } catch (Exception e) {
      log.warn("内存检查任务异常", e);
    }
  }

  /** 淘汰约一半的缓存条目 */
  private void evictHalfEntries() {
    Set<K> keys = delegate.keySet();
    int targetRemove = keys.size() / 2;
    int removed = 0;
    for (K key : keys) {
      if (removed >= targetRemove) break;
      delegate.remove(key);
      removed++;
    }
    log.info("内存感知淘汰: removed={}/{}", removed, keys.size());
  }

  /** 获取内存淘汰次数 */
  public long getEvictCount() {
    return evictCount.get();
  }

  /** 获取临界清除次数 */
  public long getCriticalEvictCount() {
    return criticalEvictCount.get();
  }

  // === 以下方法委托给底层缓存 ===

  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    return delegate.get(key, loader);
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader);
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    return delegate.putIfAbsent(key, value);
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
    delegate.putAll(map);
  }

  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    return delegate.getAll(keys);
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
  public void resetStats() {
    delegate.resetStats();
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

  @Override
  public void close() {
    // 线程池由 CacheThreadPoolManager 统一管理，不单独关闭
    log.info("MemoryAwareEvictionCache 已关闭");
  }
}
