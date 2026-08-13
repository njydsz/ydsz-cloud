package com.njydsz.common.cache.multilevel;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.internal.decorator.ExpirableCache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.stats.CacheStats;
import com.njydsz.common.cache.support.AsyncFunction;
import com.njydsz.common.cache.support.CacheThreadPoolManager;

/**
 * 多级缓存 — L1 本地缓存 + L2 Redis 分布式缓存
 *
 * <p>读取流程：
 *
 * <ol>
 *   <li>先查 L1 本地缓存，命中则直接返回
 *   <li>L1 未命中，查 L2 Redis 缓存，命中则回填 L1 并返回
 *   <li>L2 也未命中，返回 null（或调用 loader 加载）
 * </ol>
 *
 * <p>写入流程：同时写入 L1 和 L2（Write-Through 模式）
 *
 * <p>删除流程：同时从 L1 和 L2 删除
 *
 * <p>L1 独立 TTL：
 * 通过 {@link #l1TTL} 配置回填 L1 时的独立过期时间。未配置（-1）时回填条目使用 L1 缓存自身的过期策略。
 * 配置后回填条目会被包装为独立的短 TTL 控制，确保 L1 数据比 L2 更快失效，减少 L1 脏数据风险。
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>分布式微服务架构，需要跨节点缓存共享
 *   <li>高频读取 + 低频写入的热点数据
 *   <li>对缓存一致性有一定要求但可接受最终一致的场景
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 *
 * @since 1.0.0
 */
public class MultiLevelCache<K, V> implements Cache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(MultiLevelCache.class);

  /** L1 本地缓存 */
  private final Cache<K, V> l1Cache;

  /** L2 Redis 缓存 */
  private final Cache<K, V> l2Cache;

  /** L1 回填独立 TTL（纳秒），-1 表示不启用（使用 L1 自身策略） */
  private final long l1BackfillTtlNanos;

  /** L1 回填 TTL 装饰器（仅当 l1BackfillTtlNanos > 0 时启用） */
  private final Cache<K, V> l1BackfillCache;

  /** 缓存名称（用于广播失效消息） */
  private final String cacheName;

  /** 跨节点失效广播器（可选，null 表示不广播） */
  private final CacheInvalidationBroadcaster broadcaster;

  /** 分布式重建锁（可选，null 表示不加锁） */
  private final DistributedRebuildLock rebuildLock;

  /** 删除监听器列表 */
  private final List<RemovalListener<? super K, ? super V>> listeners =
      new CopyOnWriteArrayList<>();

  /** 统计计数器 */
  private final LongAdder hitCount = new LongAdder();

  private final LongAdder missCount = new LongAdder();
  private final LongAdder l1HitCount = new LongAdder();
  private final LongAdder l2HitCount = new LongAdder();
  private final LongAdder writeCount = new LongAdder();

  /**
   * 创建多级缓存（无跨节点广播）
   *
   * @param l1Cache L1 本地缓存
   * @param l2Cache L2 Redis 缓存
   */
  public MultiLevelCache(Cache<K, V> l1Cache, Cache<K, V> l2Cache) {
    this(l1Cache, l2Cache, null, null, null, -1);
  }

  /**
   * 创建多级缓存（支持跨节点 L1 失效广播）
   *
   * @param l1Cache L1 本地缓存
   * @param l2Cache L2 Redis 缓存
   * @param cacheName 缓存名称（用于广播消息标识）
   * @param broadcaster 失效广播器（null 表示不广播）
   */
  public MultiLevelCache(
      Cache<K, V> l1Cache,
      Cache<K, V> l2Cache,
      String cacheName,
      CacheInvalidationBroadcaster broadcaster) {
    this(l1Cache, l2Cache, cacheName, broadcaster, null, -1);
  }

  /**
   * 创建多级缓存（支持跨节点 L1 失效广播 + 分布式重建锁）
   *
   * @param l1Cache L1 本地缓存
   * @param l2Cache L2 Redis 缓存
   * @param cacheName 缓存名称（用于广播消息标识）
   * @param broadcaster 失效广播器（null 表示不广播）
   * @param rebuildLock 分布式重建锁（null 表示不加锁）
   */
  public MultiLevelCache(
      Cache<K, V> l1Cache,
      Cache<K, V> l2Cache,
      String cacheName,
      CacheInvalidationBroadcaster broadcaster,
      DistributedRebuildLock rebuildLock) {
    this(l1Cache, l2Cache, cacheName, broadcaster, rebuildLock, -1);
  }

  /**
   * 创建多级缓存（完整参数，支持 L1 独立回填 TTL）
   *
   * @param l1Cache L1 本地缓存
   * @param l2Cache L2 Redis 缓存
   * @param cacheName 缓存名称（用于广播消息标识）
   * @param broadcaster 失效广播器（null 表示不广播）
   * @param rebuildLock 分布式重建锁（null 表示不加锁）
   * @param l1BackfillTtlNanos L1 回填独立 TTL（纳秒），-1 表示不启用
   */
  public MultiLevelCache(
      Cache<K, V> l1Cache,
      Cache<K, V> l2Cache,
      String cacheName,
      CacheInvalidationBroadcaster broadcaster,
      DistributedRebuildLock rebuildLock,
      long l1BackfillTtlNanos) {
    this.l1Cache = l1Cache;
    this.l2Cache = l2Cache;
    this.cacheName = cacheName;
    this.broadcaster = broadcaster;
    this.rebuildLock = rebuildLock;
    this.l1BackfillTtlNanos = l1BackfillTtlNanos;

    // 当设置了 L1 独立回填 TTL 时，创建回填装饰器（write expiry only）
    if (l1BackfillTtlNanos > 0) {
      this.l1BackfillCache = new ExpirableCache<>(
          l1Cache, l1BackfillTtlNanos, 0, null, 30, 0.1);
    } else {
      this.l1BackfillCache = null;
    }

    if (broadcaster instanceof RedisCacheInvalidationBroadcaster redisBroadcaster
        && cacheName != null) {
      redisBroadcaster.registerLocalCache(cacheName, l1Cache);
    }
  }

  @Override
  public V getIfPresent(K key) {
    // 1. 先查 L1
    V value = l1Cache.getIfPresent(key);
    if (value != null) {
      hitCount.increment();
      l1HitCount.increment();
      return value;
    }

    // 2. L1 未命中，查 L2
    value = l2Cache.getIfPresent(key);
    if (value != null) {
      hitCount.increment();
      l2HitCount.increment();
      // 回填 L1：使用独立 TTL 回填或普通回填
      backfillL1(key, value);
      return value;
    }

    // 3. 都未命中
    missCount.increment();
    return null;
  }

  /**
   * 回填 L1 缓存（内部方法，支持独立 TTL 策略）
   *
   * <p>当配置了 L1 独立回填 TTL 时，通过 l1BackfillCache 写入以施加 L1 独占的短 TTL。
   * 未配置时使用原始 L1 缓存写入。
   */
  private void backfillL1(K key, V value) {
    if (l1BackfillCache != null) {
      l1BackfillCache.put(key, value);
    } else {
      l1Cache.put(key, value);
    }
    // BloomFilter 优化：标记此 key 可能被其他节点缓存（减少后续 put 时的无效广播）
    if (broadcaster instanceof BloomFilterBroadcastOptimizer bloomOpt) {
      bloomOpt.markKeyCached(key);
    }
  }

  @Override
  public V get(K key, Function<K, V> loader) {
    V value = getIfPresent(key);
    if (value == null && loader != null) {
      if (rebuildLock != null && cacheName != null) {
        // 使用分布式锁防止缓存击穿（thundering herd）
        value = rebuildLock.executeWithLock(
            cacheName,
            key,
            () -> {
              // double-check：获取锁后再次检查缓存
              V cached = getIfPresent(key);
              if (cached != null) {
                return cached;
              }
              V loaded = loader.apply(key);
              if (loaded != null) {
                put(key, loaded);
              }
              return loaded;
            });
      } else {
        value = loader.apply(key);
        if (value != null) {
          put(key, value);
        }
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
    if (loader == null) {
      return CompletableFuture.completedFuture(null);
    }
    // 使用分布式锁防止缓存击穿（与同步 get() 方法一致）
    if (rebuildLock != null && cacheName != null) {
      ExecutorService asyncExecutor = CacheThreadPoolManager.getInstance()
          .getOrCreatePool("multilevel-cache-async", 2, 8);
      return CompletableFuture.supplyAsync(
          () ->
              rebuildLock.executeWithLock(
                  cacheName,
                  key,
                  () -> {
                    // double-check：获取锁后再次检查缓存
                    V cached = getIfPresent(key);
                    if (cached != null) {
                      return cached;
                    }
                    try {
                      V loaded = loader.apply(key).join();
                      if (loaded != null) {
                        put(key, loaded);
                      }
                      return loaded;
                    } catch (Exception e) {
                      log.warn("MultiLevelCache 异步加载失败: key={}", key, e);
                      return null;
                    }
                  }),
          asyncExecutor);
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
    // Write-Through: 同时写入 L1 和 L2
    l1Cache.put(key, value);
    l2Cache.put(key, value);
    writeCount.increment();
    // 广播 L1 失效（通知其他节点清除旧值）
    if (broadcaster != null && cacheName != null) {
      broadcaster.broadcastInvalidation(cacheName, key);
    }
  }

  @Override
  public V remove(K key) {
    V value = l1Cache.getIfPresent(key);
    if (value == null) {
      value = l2Cache.getIfPresent(key);
    }
    l1Cache.remove(key);
    l2Cache.remove(key);
    if (value != null) {
      notifyRemoval(key, value, RemovalCause.EXPLICIT);
    }
    // 广播 L1 失效
    if (broadcaster != null && cacheName != null) {
      broadcaster.broadcastInvalidation(cacheName, key);
    }
    return value;
  }

  @Override
  public void clear() {
    l1Cache.clear();
    l2Cache.clear();
    // 广播全量清除
    if (broadcaster != null && cacheName != null) {
      broadcaster.broadcastClearAll(cacheName);
    }
  }

  @Override
  public long estimatedSize() {
    // 返回 L1 大小（L2 不支持高效获取大小）
    return l1Cache.estimatedSize();
  }

  @Override
  public boolean containsKey(K key) {
    return l1Cache.containsKey(key) || l2Cache.containsKey(key);
  }

  @Override
  public Set<K> keySet() {
    return l1Cache.keySet();
  }

  @Override
  public Collection<V> values() {
    return l1Cache.values();
  }

  @Override
  public Map<K, V> getAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) {
      return new HashMap<>();
    }

    Map<K, V> result = new HashMap<>(keys.size());
    List<K> l1MissedKeys = new ArrayList<>(keys.size());

    // 1. 批量查 L1
    for (K key : keys) {
      V value = l1Cache.getIfPresent(key);
      if (value != null) {
        hitCount.increment();
        l1HitCount.increment();
        result.put(key, value);
      } else {
        l1MissedKeys.add(key);
      }
    }

    // 2. L1 未命中的 key 批量查 L2（利用 multiGet）
    if (!l1MissedKeys.isEmpty()) {
      Map<K, V> l2Results = l2Cache.getAll(l1MissedKeys);
      for (K key : l1MissedKeys) {
        V value = l2Results.get(key);
        if (value != null) {
          hitCount.increment();
          l2HitCount.increment();
          // 回填 L1
          backfillL1(key, value);
          result.put(key, value);
        } else {
          missCount.increment();
        }
      }
    }

    return result;
  }

  @Override
  public void putAll(Map<K, V> map) {
    l1Cache.putAll(map);
    l2Cache.putAll(map);
    writeCount.add(map.size());
  }

  @Override
  public void removeAll(Collection<K> keys) {
    // 先删除 L1 和 L2
    keys.forEach(k -> {
      l1Cache.remove(k);
      l2Cache.remove(k);
    });
    // 批量广播 L1 失效（合并为一条广播消息）
    if (broadcaster != null && cacheName != null) {
      broadcaster.broadcastInvalidationAll(cacheName, (Collection<Object>) (Collection<?>) keys);
    }
    // 通知监听器
    for (K key : keys) {
      notifyRemoval(key, null, RemovalCause.EXPLICIT);
    }
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
    if (newValue != null) {
      put(key, newValue);
    } else {
      remove(key);
    }
    return newValue;
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> action) {
    l1Cache.forEach(action);
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
  public void addListener(RemovalListener<? super K, ? super V> listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  @Override
  public void cleanUp() {
    l1Cache.cleanUp();
    l2Cache.cleanUp();
  }

  /** 通知删除监听器 */
  private void notifyRemoval(K key, V value, RemovalCause cause) {
    for (RemovalListener<? super K, ? super V> listener : listeners) {
      try {
        listener.onRemoval(key, value, cause);
      } catch (Exception e) {
        log.warn("缓存删除监听器执行异常, key={}", key, e);
      }
    }
  }

  /** 获取 L1 命中次数 */
  public long getL1HitCount() {
    return l1HitCount.sum();
  }

  /** 获取 L2 命中次数 */
  public long getL2HitCount() {
    return l2HitCount.sum();
  }

  /** 获取写入次数 */
  public long getWriteCount() {
    return writeCount.sum();
  }

  /** 获取 L1 缓存实例 */
  public Cache<K, V> getL1Cache() {
    return l1Cache;
  }

  /** 获取 L2 缓存实例 */
  public Cache<K, V> getL2Cache() {
    return l2Cache;
  }

  /**
   * 获取 L1 回填独立 TTL（纳秒）
   *
   * @return L1 回填 TTL（纳秒），-1 表示未启用
   */
  public long getL1BackfillTtlNanos() {
    return l1BackfillTtlNanos;
  }

  /**
   * 判断是否启用了 L1 独立回填 TTL
   *
   * @return 启用时返回 true
   */
  public boolean isL1BackfillTtlEnabled() {
    return l1BackfillTtlNanos > 0;
  }
}
