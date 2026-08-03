package com.njydsz.common.cache.internal.reference;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;

/**
 * 弱引用键缓存实现。
 *
 * <p>缓存键使用 {@link WeakReference} 持有，当键对象被 GC 回收后，
 * 对应的缓存条目会在下次访问时通过 {@link ReferenceQueue} 自动清理。
 * 适用于以临时对象作为键、避免缓存阻止垃圾回收的场景。
 *
 * <p>基于 {@link ConcurrentHashMap} 实现，清理操作以 1ms 间隔惰性触发，
 * 避免频繁扫描 {@link ReferenceQueue} 的性能开销。
 *
 * @param <K> 缓存键类型（弱引用持有）
 * @param <V> 缓存值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class WeakKeyCache<K, V> extends AbstractCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(WeakKeyCache.class);

  private final ConcurrentMap<WeakReferenceKey<K>, V> map;

  private final ReferenceQueue<K> queue;

  private static final long CLEANUP_INTERVAL_NANOS = 1_000_000L;

  private volatile long lastCleanupTime = 0;

  private final boolean recordStats;

  public WeakKeyCache() {
    this(true);
  }

  public WeakKeyCache(boolean recordStats) {
    this.map = new ConcurrentHashMap<>();
    this.queue = new ReferenceQueue<>();
    this.recordStats = recordStats;
  }

  private WeakReferenceKey<K> lookupKey(K key) {
    return new WeakReferenceKey<>(key, null);
  }

  private void maybeCleanup() {
    long now = System.nanoTime();
    if (now - lastCleanupTime > CLEANUP_INTERVAL_NANOS) {
      lastCleanupTime = now;
      cleanup();
    }
  }

  private void cleanup() {
    WeakReferenceKey<? extends K> ref;
    int[] removed = {0};
    while ((ref = (WeakReferenceKey<? extends K>) queue.poll()) != null) {
      V value = map.remove(ref);
      if (value != null) {
        K key = ref.getKey();
        notifyRemoval(key, value, RemovalCause.COLLECTED);
        removed[0]++;
      }
    }
    if (removed[0] > 0) {
      log.debug("WeakKeyCache GC 清理完成，移除条目数={}", removed[0]);
    }
  }

  /**
   * 获取缓存值（不触发加载）。
   *
   * <p>通过 {@link #lookupKey} 构造等价查询键定位条目； 键对象已被 GC 回收的条目视为未命中（返回 null 并计入 miss）。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    maybeCleanup();
    V value = map.get(lookupKey(key));
    if (recordStats) {
      if (value != null) {
        hitCount.increment();
      } else {
        missCount.increment();
      }
    }
    return value;
  }

  /**
   * 写入键值对，键以弱引用形式持有。
   *
   * <p>写入前先移除同键旧条目，再以弱引用键存储，避免同键重复引用残留；
   * 键对象不再被外部强引用后，GC 即可回收该键并触发条目清理。
   *
   * @param key   缓存键（弱引用持有，可被 GC 回收）
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    maybeCleanup();
    map.remove(lookupKey(key));
    map.put(new WeakReferenceKey<>(key, queue), value);
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>成功移除时向监听器发送 {@link RemovalCause#EXPLICIT} 通知。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    maybeCleanup();
    V value = map.remove(lookupKey(key));
    if (value != null) {
      notifyRemoval(key, value, RemovalCause.EXPLICIT);
    }
    return value;
  }

  /**
   * 清空缓存。
   *
   * <p>对全部条目发送 {@link RemovalCause#EXPLICIT} 通知，同时清空底层映射与引用队列。
   *
   */
  @Override
  public void clear() {
    map.forEach(
        (ref, value) -> {
          K key = ref.getKey();
          notifyRemoval(key, value, RemovalCause.EXPLICIT);
        });
    map.clear();
    while (queue.poll() != null) {}
  }

  /**
   * 返回缓存条目数（近似值）。
   *
   * <p>基于底层映射大小，可能包含键已回收但尚未被惰性清理的残留条目。
   *
   * @return 缓存条目数
   */
  @Override
  public long estimatedSize() {
    maybeCleanup();
    return map.size();
  }

  /**
   * 判断缓存中是否存在指定键（键对象尚未被 GC 回收）。
   *
   * @param key 缓存键
   * @return 键存在时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    maybeCleanup();
    return map.containsKey(lookupKey(key));
  }

  /**
   * 返回缓存键集合。
   *
   * <p>遍历弱引用键并解包为实际键，返回一次性快照； 已回收的键（{@code get()} 返回 null）被过滤。
   *
   * @return 当前存活键的快照集合
   */
  @Override
  public Set<K> keySet() {
    maybeCleanup();
    Set<K> keys = new HashSet<>();
    for (WeakReferenceKey<K> ref : map.keySet()) {
      K key = ref.getKey();
      if (key != null) {
        keys.add(key);
      }
    }
    return keys;
  }

  /**
   * 返回缓存值集合（透传底层并发映射，迭代器弱一致）。
   *
   * @return 缓存值集合视图
   */
  @Override
  public Collection<V> values() {
    maybeCleanup();
    return map.values();
  }

  private static class WeakReferenceKey<K> extends WeakReference<K> {
    private final int hashCode;

    WeakReferenceKey(K key, ReferenceQueue<? super K> queue) {
      super(key, queue);
      this.hashCode = System.identityHashCode(key);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof WeakReferenceKey)) {
        return false;
      }
      WeakReferenceKey<?> other = (WeakReferenceKey<?>) obj;
      if (hashCode != other.hashCode) {
        return false;
      }
      K thisKey = get();
      Object otherKey = other.get();
      if (thisKey == null || otherKey == null) {
        return false;
      }
      return thisKey == otherKey;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    /**
     * 解包返回被弱引用持有的实际键。
     *
     * <p>键已被 GC 回收时返回 null，调用方需据此过滤失效条目。
     *
     * @return 实际键对象；已回收时返回 {@code null}
     */
    public K getKey() {
      return get();
    }
  }
}
