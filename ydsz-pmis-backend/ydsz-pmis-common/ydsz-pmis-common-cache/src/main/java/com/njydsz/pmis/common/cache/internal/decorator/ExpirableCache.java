package com.njydsz.pmis.common.cache.internal.decorator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.api.CachePolicy;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;
import com.njydsz.pmis.common.cache.support.CacheThreadPoolManager;
import com.njydsz.pmis.common.cache.support.Expiry;

/**
 * 过期缓存装饰器 — 为任意基础缓存叠加 TTL 过期能力
 *
 * <p>核心设计：淘汰策略（由底层缓存负责）与过期策略（由本装饰器负责）正交组合。
 * 用户可以在 LRU/TINYLFU/STRIPED 等任意淘汰策略上叠加 expireAfterWrite / expireAfterAccess /
 * 自定义 Expiry 过期策略。
 *
 * <p>工作原理：
 *
 * <ul>
 *   <li>维护一个独立的 {@link ConcurrentMap} 存储每个 key 的过期时间戳
 *   <li>读取时检查是否过期，过期则从底层缓存删除并返回 null
 *   <li>写入时计算并记录过期时间
 *   <li>后台线程定期批量清理过期条目
 * </ul>
 *
 * <p>支持三种过期模式：
 *
 * <ul>
 *   <li>expireAfterWrite：写入后固定时间过期
 *   <li>expireAfterAccess：每次访问刷新过期时间
 *   <li>Expiry（自定义）：为每个条目动态计算过期时间
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * 
 */
public class ExpirableCache<K, V> implements Cache<K, V>, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ExpirableCache.class);

  /**
   * 获取共享过期清理调度器
   *
   * <p>通过 CacheThreadPoolManager 统一管理，每次调用时获取最新实例， 避免静态初始化时序问题（Spring 后续替换全局单例时旧引用失效）。
   */
  private static ScheduledExecutorService getSharedCleaner() {
    return CacheThreadPoolManager.getInstance().getOrCreateScheduledPool("expirable-cleaner", 1);
  }

  /** 底层缓存（负责淘汰策略） */
  private final Cache<K, V> delegate;

  /** 写入后过期时间（纳秒），0 表示不使用 */
  private final long expireAfterWriteNanos;

  /** 访问后过期时间（纳秒），0 表示不使用 */
  private final long expireAfterAccessNanos;

  /** 自定义过期策略（可选） */
  private final Expiry<? super K, ? super V> expiry;

  /** 过期时间戳映射：key -> 过期时间戳持有者（纳秒） */
  private final ConcurrentMap<K, ExpiryHolder> expirationMap = new ConcurrentHashMap<>();

  /**
   * 时间桶索引：桶时间戳（纳秒，向下取整到桶大小） -> 该桶内过期的 key 集合
   *
   * <p>用于高效清理：cleanupExpired() 只扫描已过期的桶，而非全量遍历 expirationMap。
   * 对于 expireAfterAccess 场景，key 可能出现在多个桶中（刷新后旧桶残留），
   * 清理时通过 double-check expirationMap 确认是否真正过期。
   */
  private final ConcurrentSkipListMap<Long, Set<K>> expiryBuckets = new ConcurrentSkipListMap<>();

  /** 桶大小（纳秒），默认 1 秒 */
  private final long bucketSizeNanos;

  /** 清理任务 Future */
  private final ScheduledFuture<?> cleanupFuture;

  /** TTL 抖动比例（防雪崩），在原始 TTL 上加减 ±jitterRatio 的随机偏移 */
  private final double jitterRatio;

  /** 命中计数 */
  private final LongAdder hitCount = new LongAdder();

  /** 未命中计数 */
  private final LongAdder missCount = new LongAdder();

  /**
   * 可变过期时间戳持有者 — 避免每次更新都创建新的 Long 对象
   *
   * <p>对于 expireAfterAccess 模式，每次访问都需要更新过期时间戳。 使用可变持有者替代不可变 Long，避免 Map 条目替换开销和 GC 压力。
   */
  private static final class ExpiryHolder {
    volatile long expireAtNanos;

    ExpiryHolder(long expireAtNanos) {
      this.expireAtNanos = expireAtNanos;
    }
  }

  /**
   * 底层缓存淘汰监听器 — 清理 expirationMap 中已被底层淘汰的条目
   *
   * <p>解决内存泄漏：当底层缓存因容量限制淘汰条目时， expirationMap 中对应的条目不会自动清除。 通过 RemovalListener 监听淘汰事件，同步清理过期映射。
   */
  private final RemovalListener<K, V> evictionListener =
      (key, value, cause) -> {
        if (cause != RemovalCause.EXPLICIT) {
          expirationMap.remove(key);
        }
      };

  /**
   * 创建过期缓存装饰器
   *
   * @param delegate 底层缓存
   * @param expireAfterWriteNanos 写入后过期时间（纳秒），0 表示不使用
   * @param expireAfterAccessNanos 访问后过期时间（纳秒），0 表示不使用
   * @param expiry 自定义过期策略（可选，null 表示不使用）
   * @param cleanupIntervalSeconds 清理间隔（秒）
   */
  public ExpirableCache(
      Cache<K, V> delegate,
      long expireAfterWriteNanos,
      long expireAfterAccessNanos,
      Expiry<? super K, ? super V> expiry,
      long cleanupIntervalSeconds) {
    this(delegate, expireAfterWriteNanos, expireAfterAccessNanos, expiry, cleanupIntervalSeconds, 0.1);
  }

  /**
   * 创建过期缓存装饰器（带 TTL 抖动）
   *
   * @param delegate 底层缓存
   * @param expireAfterWriteNanos 写入后过期时间（纳秒），0 表示不使用
   * @param expireAfterAccessNanos 访问后过期时间（纳秒），0 表示不使用
   * @param expiry 自定义过期策略（可选，null 表示不使用）
   * @param cleanupIntervalSeconds 清理间隔（秒）
   * @param jitterRatio TTL 抖动比例（0-1，防雪崩），0 表示无抖动
   */
  public ExpirableCache(
      Cache<K, V> delegate,
      long expireAfterWriteNanos,
      long expireAfterAccessNanos,
      Expiry<? super K, ? super V> expiry,
      long cleanupIntervalSeconds,
      double jitterRatio) {
    this.delegate = delegate;
    this.expireAfterWriteNanos = expireAfterWriteNanos;
    this.expireAfterAccessNanos = expireAfterAccessNanos;
    this.expiry = expiry;
    this.jitterRatio = Math.max(0, Math.min(1, jitterRatio));
    // 桶大小固定 1 秒（见字段 Javadoc），可后续通过构造器参数暴露以支持自定义
    this.bucketSizeNanos = TimeUnit.SECONDS.toNanos(1);
    // 注册淘汰监听器，防止 expirationMap 内存泄漏
    delegate.addListener(evictionListener);
    this.cleanupFuture =
        getSharedCleaner().scheduleAtFixedRate(
            this::cleanupExpired, cleanupIntervalSeconds, cleanupIntervalSeconds, TimeUnit.SECONDS);
    log.info(
        "ExpirableCache 已创建，delegate={}, expireAfterWrite={}ns, expireAfterAccess={}ns, expiry={}, jitter={}",
        delegate.getClass().getSimpleName(),
        expireAfterWriteNanos,
        expireAfterAccessNanos,
        expiry != null ? "enabled" : "disabled",
        jitterRatio);
  }

  /** 计算 TTL 抖动后的实际过期时间（防雪崩） */
  private long applyJitter(long ttlNanos) {
    if (jitterRatio <= 0 || ttlNanos <= 0) {
      return ttlNanos;
    }
    // 在 [1 - jitterRatio, 1 + jitterRatio] 范围内随机
    double factor = 1.0 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * jitterRatio;
    return Math.max(1, (long) (ttlNanos * factor));
  }
  /** 将 key 添加到对应过期时间的桶中 */
  private void addToBucket(long expireAtNanos, K key) {
    long bucketKey = expireAtNanos / bucketSizeNanos;
    expiryBuckets.computeIfAbsent(bucketKey, k -> ConcurrentHashMap.newKeySet()).add(key);
  }

  /** 计算条目的过期时间戳（纳秒） */
  private long computeExpiration(K key, V value) {
    long now = System.nanoTime();
    if (expiry != null) {
      long ttlNanos = expiry.expireAfterCreate(key, value, now);
      return now + applyJitter(ttlNanos);
    }
    if (expireAfterWriteNanos > 0) {
      return now + applyJitter(expireAfterWriteNanos);
    }
    if (expireAfterAccessNanos > 0) {
      return now + applyJitter(expireAfterAccessNanos);
    }
    return Long.MAX_VALUE;
  }

  /** 更新条目的过期时间戳（访问后刷新） */
  private void refreshExpiration(K key, V value) {
    long now = System.nanoTime();
    if (expiry != null) {
      long ttlNanos = expiry.expireAfterRead(key, value, now);
      if (ttlNanos != Long.MAX_VALUE) {
        long newExpireAt = now + applyJitter(ttlNanos);
        ExpiryHolder holder = expirationMap.get(key);
        if (holder != null) {
          holder.expireAtNanos = newExpireAt;
        } else {
          expirationMap.put(key, new ExpiryHolder(newExpireAt));
        }
        addToBucket(newExpireAt, key);
      }
    } else if (expireAfterAccessNanos > 0) {
      long newExpireAt = now + applyJitter(expireAfterAccessNanos);
      ExpiryHolder holder = expirationMap.get(key);
      if (holder != null) {
        // 可变更新，避免 Map 条目替换
        holder.expireAtNanos = newExpireAt;
      } else {
        expirationMap.put(key, new ExpiryHolder(newExpireAt));
      }
      addToBucket(newExpireAt, key);
    }
  }

  /** 检查条目是否已过期 */
  private boolean isExpired(K key) {
    ExpiryHolder holder = expirationMap.get(key);
    if (holder == null) {
      return false;
    }
    return System.nanoTime() > holder.expireAtNanos;
  }

  /** 从底层缓存和过期映射中移除已过期条目 */
  private void removeExpired(K key) {
    expirationMap.remove(key);
    delegate.remove(key);
  }

  /**
   * 批量清理过期条目 — 基于时间桶索引高效扫描
   *
   * <p>优化：只扫描已过期桶中的 key，而非全量遍历 expirationMap。
   * 对于 expireAfterAccess 场景的旧桶残留 key，通过 double-check 确认是否真正过期。
   */
  private void cleanupExpired() {
    try {
      long now = System.nanoTime();
      long currentBucket = now / bucketSizeNanos;
      int removed = 0;

      // 只扫描 <= currentBucket 的桶（已过期或即将过期）
      NavigableMap<Long, Set<K>> expiredBuckets = expiryBuckets.headMap(currentBucket, true);
      for (Map.Entry<Long, Set<K>> bucket : expiredBuckets.entrySet()) {
        for (K key : bucket.getValue()) {
          ExpiryHolder holder = expirationMap.get(key);
          // Double-check：确认 key 仍在 expirationMap 中且确实已过期
          // （expireAfterAccess 可能已将过期时间刷新到更晚的桶）
          if (holder != null && holder.expireAtNanos <= now) {
            expirationMap.remove(key);
            delegate.remove(key);
            removed++;
          }
        }
        expiryBuckets.remove(bucket.getKey());
      }
      if (removed > 0) {
        log.debug("ExpirableCache 清理过期条目: removed={}", removed);
      }
    } catch (Exception e) {
      log.warn("ExpirableCache 清理任务异常", e);
    }
  }

  @Override
  public V getIfPresent(K key) {
    V value = delegate.getIfPresent(key);
    if (value == null) {
      missCount.increment();
      return null;
    }
    if (isExpired(key)) {
      removeExpired(key);
      missCount.increment();
      return null;
    }
    hitCount.increment();
    refreshExpiration(key, value);
    return value;
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value == null && loader != null) {
      value = loader.apply(key);
      if (value != null) {
        put(key, value);
      }
    }
    return value;
  }

  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    V value = getIfPresent(key);
    if (value != null) {
      return CompletableFuture.completedFuture(value);
    }
    return loader
        .apply(key)
        .thenApply(
            v -> {
              if (v != null) {
                put(key, v);
              }
              return v;
            });
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
    long expireAt = computeExpiration(key, value);
    expirationMap.put(key, new ExpiryHolder(expireAt));
    addToBucket(expireAt, key);
  }

  @Override
  public V putIfAbsent(K key, V value) {
    V existing = getIfPresent(key);
    if (existing == null) {
      put(key, value);
      return null;
    }
    return existing;
  }

  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    V value = getIfPresent(key);
    if (value == null) {
      value = mappingFunction.apply(key);
      if (value != null) {
        put(key, value);
      }
    }
    return value;
  }

  @Override
  public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    V oldValue = getIfPresent(key);
    V newValue = remappingFunction.apply(key, oldValue);
    if (newValue == null) {
      remove(key);
    } else {
      put(key, newValue);
    }
    return newValue;
  }

  @Override
  public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
    V oldValue = getIfPresent(key);
    V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
    if (newValue == null) {
      remove(key);
    } else {
      put(key, newValue);
    }
    return newValue;
  }

  @Override
  public V remove(K key) {
    expirationMap.remove(key);
    // 桶中的残留条目由 cleanupExpired 自动清理，无需主动移除
    return delegate.remove(key);
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
  public void clear() {
    expirationMap.clear();
    expiryBuckets.clear();
    delegate.clear();
  }

  @Override
  public void putAll(Map<K, V> map) {
    if (map == null || map.isEmpty()) {
      return;
    }
    map.forEach(this::put);
  }

  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<K, V> result = new HashMap<>(keys.size());
    for (K key : keys) {
      V value = getIfPresent(key);
      if (value != null) {
        result.put(key, value);
      }
    }
    return result;
  }

  @Override
  public void removeAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (K key : keys) {
      remove(key);
    }
  }

  @Override
  public long estimatedSize() {
    return delegate.estimatedSize();
  }

  @Override
  public boolean isEmpty() {
    return estimatedSize() == 0;
  }

  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.sum(), missCount.sum());
  }

  @Override
  public CachePolicy policy() {
    return new CachePolicy() {
      @Override
      public Optional<EvictionPolicy> eviction() {
        return delegate.policy().eviction();
      }

      @Override
      public Optional<ExpirationPolicy> expiration() {
        return Optional.of(
            new ExpirationPolicy() {
              @Override
              public long getExpiresAfterWriteNanos() {
                return expireAfterWriteNanos;
              }

              @Override
              public void setExpiresAfterWriteNanos(long expireAfterWriteNanos) {
                // 过期时间在构造时固定，运行时不支持修改
              }

              @Override
              public long getExpiresAfterAccessNanos() {
                return expireAfterAccessNanos;
              }

              @Override
              public void setExpiresAfterAccessNanos(long expireAfterAccessNanos) {
                // 过期时间在构造时固定，运行时不支持修改
              }

              @Override
              public boolean isCustomExpiry() {
                return expiry != null;
              }
            });
      }
    };
  }

  @Override
  public boolean containsKey(K key) {
    if (!delegate.containsKey(key)) {
      return false;
    }
    if (isExpired(key)) {
      removeExpired(key);
      return false;
    }
    return true;
  }

  @Override
  public Set<K> keySet() {
    Set<K> keys = new HashSet<>();
    for (K key : delegate.keySet()) {
      if (!isExpired(key)) {
        keys.add(key);
      }
    }
    return keys;
  }

  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>();
    for (K key : delegate.keySet()) {
      if (!isExpired(key)) {
        V value = delegate.getIfPresent(key);
        if (value != null) {
          values.add(value);
        }
      }
    }
    return values;
  }

  @Override
  public void cleanUp() {
    cleanupExpired();
    delegate.cleanUp();
  }

  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    for (K key : delegate.keySet()) {
      if (!isExpired(key)) {
        V value = delegate.getIfPresent(key);
        if (value != null) {
          action.accept(key, value);
        }
      }
    }
  }

  /** 获取底层缓存实例 */
  public Cache<K, V> getDelegate() {
    return delegate;
  }

  @Override
  public void close() {
    if (cleanupFuture != null) {
      cleanupFuture.cancel(false);
    }
    log.info("ExpirableCache 已关闭");
  }
}
