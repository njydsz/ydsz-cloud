package com.njydsz.pmis.common.cache.multilevel;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.listener.RemovalListener;
import com.njydsz.pmis.common.cache.stats.CacheStats;
import com.njydsz.pmis.common.cache.support.AsyncFunction;

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
 * @author ydsz-pmis-team
 * 
 */
public class MultiLevelCache<K, V> implements Cache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(MultiLevelCache.class);

  /** L1 本地缓存 */
  private final Cache<K, V> l1Cache;

  /** L2 Redis 缓存 */
  private final Cache<K, V> l2Cache;

  /** 缓存名称（用于广播失效消息） */
  private final String cacheName;

  /** 跨节点失效广播器（可选，null 表示不广播） */
  private final CacheInvalidationBroadcaster broadcaster;

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
    this(l1Cache, l2Cache, null, null);
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
    this.l1Cache = l1Cache;
    this.l2Cache = l2Cache;
    this.cacheName = cacheName;
    this.broadcaster = broadcaster;
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
      // 回填 L1
      l1Cache.put(key, value);
      return value;
    }

    // 3. 都未命中
    missCount.increment();
    return null;
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
          l1Cache.put(key, value);
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
    keys.forEach(this::remove);
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
}
