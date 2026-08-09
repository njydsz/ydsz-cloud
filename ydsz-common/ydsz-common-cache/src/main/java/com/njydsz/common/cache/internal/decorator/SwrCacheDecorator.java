package com.njydsz.common.cache.internal.decorator;

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

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheLoader;

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
 * @author ydsz-team
 *
 * @since 1.0.0
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

  /**
   * 获取缓存值（不触发加载），按 SWR 语义区分新鲜期/陈旧期。
   *
   * <p>返回行为：新鲜期内直接返回；陈旧期内返回旧值并异步触发刷新； 超过陈旧期返回 {@code null} 交由调用方同步加载。
   * 没有写入时间戳的条目（如底层缓存被外部写入）视为新鲜，直接返回。
   *
   * @param key 缓存键
   * @return 缓存值；无值或已超过陈旧期时返回 {@code null}
   */
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

  /**
   * 获取缓存值，未命中或超过陈旧期时使用调用方加载器同步加载。
   *
   * <p>同步加载成功（非 null）后写入缓存并重置写入时间戳，使其进入新的新鲜期。
   *
   * @param key     缓存键
   * @param loaderFn 调用方提供的值加载器
   * @return 缓存值或加载的新值
   */
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

  /**
   * 写入键值对，并记录写入时间戳用于 SWR 新鲜期判断。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
    writeTimestamps.put(key, System.nanoTime());
  }

  /**
   * 移除指定键并返回被移除的值，同时清除其 SWR 时间戳。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    writeTimestamps.remove(key);
    return delegate.remove(key);
  }

  /**
   * 清空缓存及全部 SWR 状态（写入时间戳、进行中的刷新任务记录）。
   *
   * <p>注意：清空不会中断已在执行中的异步刷新。
   */
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

  /**
   * 仅当键不存在时写入，并记录写入时间戳。
   *
   * <p>仅在实际写入成功（旧值不存在）时更新时间戳，避免覆盖已有条目的 SWR 状态。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @return 已存在的旧值；键原本不存在时返回 {@code null}
   */
  @Override
  public V putIfAbsent(K key, V value) {
    V existing = delegate.putIfAbsent(key, value);
    if (existing == null) {
      writeTimestamps.put(key, System.nanoTime());
    }
    return existing;
  }

  /**
   * 计算并写入缓存（直接委托，不维护 SWR 时间戳）。
   *
   * <p>注意：绕过本类的 SWR 语义，新写入的条目可能被 {@link #getIfPresent} 判为"无时间戳"而视为新鲜。
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
   * 基于旧值重新计算并写回，结果非 null 时刷新 SWR 时间戳。
   *
   * @param key               缓存键
   * @param remappingFunction 重映射函数
   * @return 重映射后的值
   */
  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    V result = delegate.compute(key, remappingFunction);
    if (result != null) {
      writeTimestamps.put(key, System.nanoTime());
    }
    return result;
  }

  /**
   * 合并值与现有值，结果非 null 时刷新 SWR 时间戳。
   *
   * @param key               缓存键
   * @param value             待合并的值
   * @param remappingFunction 合并函数
   * @return 合并后的值
   */
  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    V result = delegate.merge(key, value, remappingFunction);
    if (result != null) {
      writeTimestamps.put(key, System.nanoTime());
    }
    return result;
  }

  /**
   * 异步获取缓存值（直接委托，不参与 SWR 判断）。
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
   * 批量写入，并为全部条目统一记录写入时间戳。
   *
   * @param map 待写入的映射
   */
  @Override
  public void putAll(Map<K, V> map) {
    delegate.putAll(map);
    long now = System.nanoTime();
    map.forEach((k, v) -> writeTimestamps.put(k, now));
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
   * 批量移除指定键，并清除对应 SWR 时间戳。
   *
   * @param keys 待移除的键集合
   */
  @Override
  public void removeAll(Collection<K> keys) {
    keys.forEach(writeTimestamps::remove);
    delegate.removeAll(keys);
  }

  /**
   * 使单个键失效（等价于 {@link #remove}）。
   *
   * @param key 缓存键
   */
  @Override
  public void invalidate(K key) {
    remove(key);
  }

  /**
   * 批量使指定键集合失效（等价于 {@link #removeAll}）。
   *
   * @param keys 待失效的键集合
   */
  @Override
  public void invalidateAll(Collection<K> keys) {
    removeAll(keys);
  }

  /**
   * 使全部键失效（等价于 {@link #clear}）。
   */
  @Override
  public void invalidateAll() {
    clear();
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
}
