package com.njydsz.common.cache.internal.decorator;

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

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheThreadPoolManager;
import com.njydsz.common.cache.support.Expiry;

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
 * @author ydsz-team
 *
 * @since 1.0.0
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
  private volatile long expireAfterWriteNanos;

  /** 访问后过期时间（纳秒），0 表示不使用 */
  private volatile long expireAfterAccessNanos;

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

  /**
   * 获取缓存值（不触发加载），并检查是否过期。
   *
   * <p>过期语义：读路径先查底层缓存，命中后再比对过期时间戳； 已过期则同步删除并返回 null（计入未命中）。 未过期时按
   * expireAfterAccess / 自定义 Expiry 刷新过期时间（写后过期模式不受影响）。
   *
   * @param key 缓存键
   * @return 缓存值；未命中或已过期时返回 {@code null}
   */
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

  /**
   * 获取缓存值，未命中时使用加载器加载，并按写后过期语义落缓存。
   *
   * @param key    缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的新值
   */
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

  /**
   * 异步获取缓存值，未命中时使用异步加载器加载并写入（写后过期）。
   *
   * @param key    缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
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

  /**
   * 写入键值对，并记录新的过期时间。
   *
   * <p>先写入底层缓存，再按配置的过期策略（自定义 Expiry > 写后过期 > 访问后过期） 计算并登记过期时间戳，
   * 同时加入时间桶索引供后台清理使用。 TTL 为 0 且无自定义策略时条目不设过期（Long.MAX_VALUE）。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
    long expireAt = computeExpiration(key, value);
    expirationMap.put(key, new ExpiryHolder(expireAt));
    addToBucket(expireAt, key);
  }

  /**
   * 仅当键不存在（含已过期）时写入并返回旧值。
   *
   * <p>过期键视为不存在，允许覆盖写入。
   *
   * @param key   缓存键
   * @param value 缓存值
   * @return 已存在的旧值；键不存在或已过期时返回 {@code null}
   */
  @Override
  public V putIfAbsent(K key, V value) {
    V existing = getIfPresent(key);
    if (existing == null) {
      put(key, value);
      return null;
    }
    return existing;
  }

  /**
   * 键不存在或已过期时计算并写入，返回计算值。
   *
   * @param key             缓存键
   * @param mappingFunction 映射函数
   * @return 缓存值或计算的新值
   */
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

  /**
   * 基于旧值重新计算并写回，重映射结果为 null 时删除该键。
   *
   * @param key               缓存键
   * @param remappingFunction 重映射函数
   * @return 重映射后的值；结果为 null 表示已删除
   */
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

  /**
   * 合并值与现有值并写回，合并结果为 null 时删除该键。
   *
   * @param key               缓存键
   * @param value             待合并的值
   * @param remappingFunction 合并函数
   * @return 合并后的值
   */
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

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>同步清除过期时间戳映射；时间桶中的残留索引由后台 {@code cleanupExpired} 回收， 无需在此处主动清理。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    expirationMap.remove(key);
    // 桶中的残留条目由 cleanupExpired 自动清理，无需主动移除
    return delegate.remove(key);
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
   * 批量使指定键集合失效。
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
   * 清空缓存，同时清空过期时间戳映射与时间桶索引。
   */
  @Override
  public void clear() {
    expirationMap.clear();
    expiryBuckets.clear();
    delegate.clear();
  }

  /**
   * 批量写入，逐条按写后过期语义登记过期时间。
   *
   * @param map 待写入的映射
   */
  @Override
  public void putAll(Map<K, V> map) {
    if (map == null || map.isEmpty()) {
      return;
    }
    map.forEach(this::put);
  }

  /**
   * 批量获取指定键的缓存值（不触发加载），自动过滤已过期条目。
   *
   * @param keys 待获取的键集合
   * @return 命中键值映射；未命中或已过期的键不会出现在结果中
   */
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

  /**
   * 批量移除指定键。
   *
   * @param keys 待移除的键集合
   */
  @Override
  public void removeAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (K key : keys) {
      remove(key);
    }
  }

  /**
   * 返回缓存条目数（近似值）。
   *
   * <p>透传底层缓存的估算值，未剔除尚未被后台清理的过期条目。
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
    return estimatedSize() == 0;
  }

  /**
   * 获取缓存命中率。
   *
   * <p>过期导致的未命中同样计入 miss。
   *
   * @return 命中率，范围 [0.0, 1.0]
   */
  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  /**
   * 获取缓存统计快照。
   *
   * @return 包含命中数与未命中数的统计对象
   */
  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.sum(), missCount.sum());
  }

  /**
   * 获取缓存策略查询接口。
   *
   * <p>淘汰策略透传底层缓存；过期策略由本装饰器提供， 但过期时间在构造时固定，运行时修改过期时间的 setter 均为空操作。
   *
   * @return 缓存策略；过期策略始终可用
   */
  @Override
  public CachePolicy policy() {
    return new CachePolicy() {
      /**
       * 查询底层缓存的淘汰策略。
       *
       * @return 底层缓存支持的淘汰策略；不支持时返回空 Optional
       */
      @Override
      public Optional<EvictionPolicy> eviction() {
        return delegate.policy().eviction();
      }

      /**
       * 查询本装饰器配置的过期策略。
       *
       * @return 过期策略；因本类总是管理过期时间，始终非空
       */
      @Override
      public Optional<ExpirationPolicy> expiration() {
        return Optional.of(
            new ExpirationPolicy() {
              /**
               * 获取写后过期时间（纳秒）。
               *
               * @return 写后过期纳秒数；0 表示未启用该模式
               */
              @Override
              public long getExpiresAfterWriteNanos() {
                return expireAfterWriteNanos;
              }

              /**
               * 修改写后过期时间。
               *
               * <p>动态调整立即生效：新写入条目使用新 TTL，
               * 已写入条目以其写入时计算的过期时间为准（不会追溯）。
               *
               * <p>设置为 0 表示禁用写后过期。
               *
               * @param expireAfterWriteNanos 期望的写后过期纳秒数
               */
              @Override
              public void setExpiresAfterWriteNanos(long expireAfterWriteNanos) {
                if (expireAfterWriteNanos < 0) {
                  throw new IllegalArgumentException("expireAfterWriteNanos must be >= 0");
                }
                ExpirableCache.this.expireAfterWriteNanos = expireAfterWriteNanos;
              }

              /**
               * 获取访问后过期时间（纳秒）。
               *
               * @return 访问后过期纳秒数；0 表示未启用该模式
               */
              @Override
              public long getExpiresAfterAccessNanos() {
                return expireAfterAccessNanos;
              }

              /**
               * 修改访问后过期时间。
               *
               * <p>动态调整立即生效：新访问条目使用新 TTL，
               * 已写入条目以其原有过期时间为准（不会追溯）。
               *
               * <p>设置为 0 表示禁用访问后过期。
               *
               * @param expireAfterAccessNanos 期望的访问后过期纳秒数
               */
              @Override
              public void setExpiresAfterAccessNanos(long expireAfterAccessNanos) {
                if (expireAfterAccessNanos < 0) {
                  throw new IllegalArgumentException("expireAfterAccessNanos must be >= 0");
                }
                ExpirableCache.this.expireAfterAccessNanos = expireAfterAccessNanos;
              }

              /**
               * 是否使用自定义过期策略。
               *
               * @return 构造时传入了 {@link Expiry} 时返回 {@code true}
               */
              @Override
              public boolean isCustomExpiry() {
                return expiry != null;
              }
            });
      }
    };
  }

  /**
   * 判断缓存中是否存在指定键（未过期的）。
   *
   * <p>底层存在但已过期的键会被同步删除并视为不存在。
   *
   * @param key 缓存键
   * @return 键存在且未过期时返回 {@code true}
   */
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

  /**
   * 返回缓存键集合，已自动过滤过期键。
   *
   * <p>返回一次性快照，不含底层缓存的实时视图语义。
   *
   * @return 当前未过期键的快照集合
   */
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

  /**
   * 返回缓存值集合，已自动过滤过期条目。
   *
   * <p>返回一次性快照，值可能重复。
   *
   * @return 当前未过期值的快照集合
   */
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

  /**
   * 执行缓存维护操作。
   *
   * <p>先立即清理本装饰器的过期条目（复用后台任务的时间桶扫描逻辑）， 再透传底层缓存的维护动作。
   */
  @Override
  public void cleanUp() {
    cleanupExpired();
    delegate.cleanUp();
  }

  /**
   * 添加删除监听器（透传底层缓存）。
   *
   * <p>注意：本装饰器内部已注册了一个用于清理过期映射的监听器， 外部监听器与其共存，互不影响。
   *
   * @param listener 删除监听器
   */
  @Override
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    delegate.addListener(listener);
  }

  /**
   * 遍历缓存键值对（自动过滤过期条目）。
   *
   * @param action 作用于每个未过期键值对的消费动作
   */
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

  /**
   * 关闭过期缓存，取消后台清理任务。
   *
   * <p>取消任务采用 {@code cancel(false)}，不中断正在执行的清理； 不关闭底层缓存与共享线程池，避免影响其他实例。
   */
  @Override
  public void close() {
    if (cleanupFuture != null) {
      cleanupFuture.cancel(false);
    }
    log.info("ExpirableCache 已关闭");
  }
}
