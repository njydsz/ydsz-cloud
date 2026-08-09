package com.njydsz.common.cache.internal.concurrent;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.support.AsyncFunction;

/**
 * 并发安全缓存实现。
 *
 * <p>基于 {@link ConcurrentHashMap} 实现线程安全的缓存， 适用于高并发读写场景。提供原子性的 {@code get(key, loader)} 和 {@code
 * getAsync(key, loader)} 操作，避免缓存击穿。
 *
 * <p>与 {@link LRUCache} 不同，本类不提供淘汰策略，缓存容量无上限。 如需容量限制，请使用 {@link LRUCache} 配合 {@link
 * StripedConcurrentCache} 分段锁。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 *
 * @since 1.0.0
 */
public class ConcurrentCache<K, V> extends AbstractCache<K, V> {

  /** 底层并发存储映射 */
  private final ConcurrentMap<K, V> map;

  /** 创建默认初始容量的并发缓存。 */
  public ConcurrentCache() {
    this(16);
  }

  /**
   * 创建指定初始容量的并发缓存。
   *
   * @param initialCapacity 初始容量
   */
  public ConcurrentCache(int initialCapacity) {
    this(initialCapacity, 16);
  }

  /**
   * 创建指定初始容量和并发级别的并发缓存。
   *
   * @param initialCapacity 初始容量
   * @param concurrencyLevel 并发级别（预估同时写入的线程数）
   */
  public ConcurrentCache(int initialCapacity, int concurrencyLevel) {
    this.map = new ConcurrentHashMap<>(initialCapacity, 0.75f, concurrencyLevel);
  }

  /**
   * 获取缓存值（不触发加载）。
   *
   * <p>命中时递增命中计数，未命中时递增未命中计数并返回 null； null 键直接返回 null（未命中语义）。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    V value = map.get(key);
    if (value != null) {
      hitCount.increment();
    } else {
      missCount.increment();
    }
    return value;
  }

  /**
   * 获取缓存值，如果不存在则使用加载器原子性加载。
   *
   * <p>使用 {@code putIfAbsent} 保证原子性，避免多个线程同时加载同一键值。
   *
   * @param key 缓存键
   * @param loader 值加载器
   * @return 缓存值或加载的值
   */
  @Override
  public V get(K key, Function<K, V> loader) {
    V value = map.get(key);
    if (value != null) {
      hitCount.increment();
      return value;
    }

    missCount.increment();
    if (loader != null) {
      value = loader.apply(key);
      if (value != null) {
        V existing = map.putIfAbsent(key, value);
        if (existing != null) {
          value = existing;
        }
      }
    }
    return value;
  }

  /**
   * 异步获取缓存值，如果不存在则使用异步加载器原子性加载。
   *
   * <p>使用 {@code putIfAbsent} 保证原子性，避免多个线程同时加载同一键值。
   *
   * @param key 缓存键
   * @param loader 异步值加载器
   * @return 异步完成的缓存值
   */
  @Override
  public CompletableFuture<V> getAsync(K key, AsyncFunction<K, V> loader) {
    V value = map.get(key);
    if (value != null) {
      hitCount.increment();
      return CompletableFuture.completedFuture(value);
    }

    missCount.increment();
    return loader
        .apply(key)
        .thenApply(
            v -> {
              if (v != null) {
                V existing = map.putIfAbsent(key, v);
                return existing != null ? existing : v;
              }
              return v;
            });
  }

  /**
   * 写入键值对。
   *
   * <p>若键已存在则覆盖旧值，并向删除监听器发出 {@link RemovalCause#REPLACED} 通知；
   * 无监听器时走无通知快路径以降低开销。value 为 null 时直接存入底层 Map（是否允许由调用方约定）。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    if (!listeners.isEmpty()) {
      V oldValue = map.put(key, value);
      if (oldValue != null) {
        notifyRemoval(key, oldValue, RemovalCause.REPLACED);
      }
    } else {
      map.put(key, value);
    }
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>成功移除（旧值非 null）且存在监听器时，发出 {@link RemovalCause#EXPLICIT} 通知。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    V value = map.remove(key);
    if (value != null && !listeners.isEmpty()) {
      notifyRemoval(key, value, RemovalCause.EXPLICIT);
    }
    return value;
  }

  /**
   * 清空缓存。
   *
   * <p>先对存量条目逐一发送 {@link RemovalCause#EXPLICIT} 删除通知，再执行清空，
   * 保证监听器能感知被清理的每个键值对。
   */
  @Override
  public void clear() {
    forEach((key, value) -> notifyRemoval(key, value, RemovalCause.EXPLICIT));
    map.clear();
  }

  /**
   * 返回当前缓存条目的精确数量。
   *
   * <p>委托 {@link ConcurrentHashMap#size()}，在并发写入下为弱一致的近似统计。
   *
   * @return 缓存条目数
   */
  @Override
  public long estimatedSize() {
    return map.size();
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * @param key 缓存键
   * @return 键存在时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  /**
   * 返回缓存键集合视图。
   *
   * <p>透传 {@link ConcurrentHashMap#keySet()}，迭代器为弱一致：迭代期间可见并发修改，但可能遗漏或重复。
   *
   * @return 缓存键集合视图
   */
  @Override
  public Set<K> keySet() {
    return map.keySet();
  }

  /**
   * 返回缓存值集合视图。
   *
   * <p>透传 {@link ConcurrentHashMap#values()}，迭代器为弱一致，迭代期间并发修改可能不被完整反映。
   *
   * @return 缓存值集合视图
   */
  @Override
  public Collection<V> values() {
    return map.values();
  }
}
