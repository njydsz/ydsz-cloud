package com.njydsz.pmis.common.cache.internal.decorator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;
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
 * @author Marvin Lee
 * @version 4.1.0
 */
public class ExpirableCache<K, V> implements Cache<K, V>, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ExpirableCache.class);

  /** 全局共享过期清理调度器（守护线程） */
  private static final ScheduledExecutorService SHARED_CLEANER =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "ExpirableCache-Cleaner");
            t.setDaemon(true);
            return t;
          });

  /** 底层缓存（负责淘汰策略） */
  private final Cache<K, V> delegate;

  /** 写入后过期时间（纳秒），0 表示不使用 */
  private final long expireAfterWriteNanos;

  /** 访问后过期时间（纳秒），0 表示不使用 */
  private final long expireAfterAccessNanos;

  /** 自定义过期策略（可选） */
  private final Expiry<? super K, ? super V> expiry;

  /** 过期时间戳映射：key -> 过期时间戳（纳秒） */
  private final ConcurrentMap<K, Long> expirationMap = new ConcurrentHashMap<>();

  /** 是否使用访问后过期 */
  private final boolean accessMode;

  /** 清理任务 Future */
  private final java.util.concurrent.ScheduledFuture<?> cleanupFuture;

  /** 命中计数 */
  private final AtomicLong hitCount = new AtomicLong(0);

  /** 未命中计数 */
  private final AtomicLong missCount = new AtomicLong(0);

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
    this.delegate = delegate;
    this.expireAfterWriteNanos = expireAfterWriteNanos;
    this.expireAfterAccessNanos = expireAfterAccessNanos;
    this.expiry = expiry;
    this.accessMode = expireAfterAccessNanos > 0;
    this.cleanupFuture =
        SHARED_CLEANER.scheduleAtFixedRate(
            this::cleanupExpired, cleanupIntervalSeconds, cleanupIntervalSeconds, TimeUnit.SECONDS);
    log.info(
        "ExpirableCache 已创建，delegate={}, expireAfterWrite={}ns, expireAfterAccess={}ns, expiry={}",
        delegate.getClass().getSimpleName(),
        expireAfterWriteNanos,
        expireAfterAccessNanos,
        expiry != null ? "enabled" : "disabled");
  }

  /** 计算条目的过期时间戳（纳秒） */
  private long computeExpiration(K key, V value) {
    long now = System.nanoTime();
    if (expiry != null) {
      long ttlNanos = expiry.expireAfterCreate(key, value, now);
      return now + ttlNanos;
    }
    if (expireAfterWriteNanos > 0) {
      return now + expireAfterWriteNanos;
    }
    if (expireAfterAccessNanos > 0) {
      return now + expireAfterAccessNanos;
    }
    return Long.MAX_VALUE;
  }

  /** 更新条目的过期时间戳（访问后刷新） */
  private void refreshExpiration(K key, V value) {
    long now = System.nanoTime();
    if (expiry != null) {
      long ttlNanos = expiry.expireAfterRead(key, value, now);
      if (ttlNanos != Long.MAX_VALUE) {
        expirationMap.put(key, now + ttlNanos);
      }
    } else if (expireAfterAccessNanos > 0) {
      expirationMap.put(key, now + expireAfterAccessNanos);
    }
  }

  /** 检查条目是否已过期 */
  private boolean isExpired(K key) {
    Long expiration = expirationMap.get(key);
    if (expiration == null) {
      return false;
    }
    return System.nanoTime() > expiration;
  }

  /** 从底层缓存和过期映射中移除已过期条目 */
  private void removeExpired(K key) {
    expirationMap.remove(key);
    delegate.remove(key);
  }

  /** 批量清理过期条目 */
  private void cleanupExpired() {
    try {
      long now = System.nanoTime();
      int removed = 0;
      for (Map.Entry<K, Long> entry : expirationMap.entrySet()) {
        if (entry.getValue() < now) {
          K key = entry.getKey();
          expirationMap.remove(key);
          delegate.remove(key);
          removed++;
        }
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
      missCount.incrementAndGet();
      return null;
    }
    if (isExpired(key)) {
      removeExpired(key);
      missCount.incrementAndGet();
      return null;
    }
    hitCount.incrementAndGet();
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
    expirationMap.put(key, computeExpiration(key, value));
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
      return java.util.Collections.emptyMap();
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
    long total = hitCount.get() + missCount.get();
    return total == 0 ? 0.0 : (double) hitCount.get() / total;
  }

  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.get(), missCount.get());
  }

  @Override
  public com.njydsz.pmis.common.cache.api.CachePolicy policy() {
    return new com.njydsz.pmis.common.cache.api.CachePolicy() {
      @Override
      public java.util.Optional<EvictionPolicy> eviction() {
        return delegate.policy().eviction();
      }

      @Override
      public java.util.Optional<ExpirationPolicy> expiration() {
        return java.util.Optional.of(
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
