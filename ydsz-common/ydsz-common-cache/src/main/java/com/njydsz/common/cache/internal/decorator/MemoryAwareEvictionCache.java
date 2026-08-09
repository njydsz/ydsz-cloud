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
 * @since 1.0.0
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

  /**
   * 获取缓存值（不触发加载），直接透传底层缓存。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    return delegate.getIfPresent(key);
  }

  /**
   * 获取缓存值，未命中时使用加载器加载。
   *
   * @param key    缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的新值
   */
  @Override
  public V get(K key, Function<K, V> loader) {
    return delegate.get(key, loader);
  }

  /**
   * 异步获取缓存值，未命中时使用异步加载器加载。
   *
   * @param key    缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    return delegate.getAsync(key, loader);
  }

  /**
   * 写入键值对。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  /**
   * 仅当键不存在时写入并返回旧值。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @return 已存在的旧值；键原本不存在时返回 {@code null}
   */
  @Override
  public V putIfAbsent(K key, V value) {
    return delegate.putIfAbsent(key, value);
  }

  /**
   * 计算并写入缓存。
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
   * 基于旧值重新计算映射并写回缓存。
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
   * 合并值与现有值。
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
   * 移除指定键并返回被移除的值。
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
   * 清空缓存。
   */
  @Override
  public void clear() {
    delegate.clear();
  }

  /**
   * 批量写入。
   *
   * @param map 待写入的映射
   */
  @Override
  public void putAll(Map<K, V> map) {
    delegate.putAll(map);
  }

  /**
   * 批量获取指定键的缓存值（不触发加载）。
   *
   * @param keys 待获取的键集合
   * @return 命中键值映射；未命中的键不会出现在结果中
   */
  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    return delegate.getAll(keys);
  }

  /**
   * 批量移除指定键。
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
   * 重置统计计数器。
   */
  @Override
  public void resetStats() {
    delegate.resetStats();
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

  /**
   * 关闭内存感知缓存。
   *
   * <p>仅记录关闭日志，不关闭定时监控线程——线程由 {@link CacheThreadPoolManager} 统一管理， 避免影响共享线程池生命周期。
   */
  @Override
  public void close() {
    // 线程池由 CacheThreadPoolManager 统一管理，不单独关闭
    log.info("MemoryAwareEvictionCache 已关闭");
  }
}
