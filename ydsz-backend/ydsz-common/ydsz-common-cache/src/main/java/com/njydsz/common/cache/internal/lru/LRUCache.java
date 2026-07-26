package com.njydsz.common.cache.internal.lru;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;

/**
 * 基于 {@link LinkedHashMap} 的 LRU（最近最少使用）缓存实现。
 *
 * <p>使用 {@link StampedLock} 保证并发安全，access-order 模式下 get 操作需要写锁
 * （因为会修改链表结构）。淘汰条目通过 ThreadLocal 队列延迟通知监听器，
 * 避免在写锁内触发回调导致死锁。
 *
 * <p>支持最大容量限制、命中/未命中统计、移除通知监听器。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class LRUCache<K, V> extends AbstractCache<K, V> {

  private final LinkedHashMap<K, V> map;

  private final StampedLock lock = new StampedLock();

  private final boolean recordStats;

  private final int maxSize;

  private final ThreadLocal<ArrayDeque<Map.Entry<K, V>>> pendingEvictions =
      ThreadLocal.withInitial(ArrayDeque::new);

  public LRUCache(int maxSize) {
    this(maxSize, Math.max(16, maxSize), true);
  }

  public LRUCache(int maxSize, int initialCapacity) {
    this(maxSize, initialCapacity, true);
  }

  public LRUCache(int maxSize, int initialCapacity, boolean recordStats) {
    this.maxSize = maxSize;
    this.map =
        new LinkedHashMap<K, V>(initialCapacity, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            boolean remove = size() > LRUCache.this.maxSize;
            if (remove) {
              pendingEvictions.get().addLast(eldest);
            }
            return remove;
          }
        };
    this.recordStats = recordStats;
  }

  private void drainEvictions() {
    ArrayDeque<Map.Entry<K, V>> queue = pendingEvictions.get();
    Map.Entry<K, V> entry;
    while ((entry = queue.pollFirst()) != null) {
      notifyRemoval(entry.getKey(), entry.getValue(), RemovalCause.SIZE);
    }
  }

  @Override
  public V getIfPresent(K key) {
    // LinkedHashMap 在 access-order 模式下 get 会修改链表结构，
    // 不能使用 StampedLock 乐观读（乐观读期间不允许结构性修改），
    // 必须使用写锁保证安全
    long stamp = lock.writeLock();
    try {
      V value = map.get(key);
      if (recordStats) {
        if (value != null) {
          hitCount.increment();
        } else {
          missCount.increment();
        }
      }
      return value;
    } finally {
      lock.unlockWrite(stamp);
      drainEvictions();
    }
  }

  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    long stamp = lock.writeLock();
    try {
      V value = map.get(key);
      if (value != null) {
        if (recordStats) {
          hitCount.increment();
        }
        return value;
      }
      if (recordStats) {
        missCount.increment();
      }
      value = mappingFunction.apply(key);
      if (value != null) {
        map.put(key, value);
      }
      return value;
    } finally {
      lock.unlockWrite(stamp);
      drainEvictions();
    }
  }

  @Override
  public void put(K key, V value) {
    long stamp = lock.writeLock();
    try {
      V oldValue = map.put(key, value);
      if (oldValue != null && !listeners.isEmpty()) {
        notifyRemoval(key, oldValue, RemovalCause.REPLACED);
      }
    } finally {
      lock.unlockWrite(stamp);
      drainEvictions();
    }
  }

  @Override
  public V remove(K key) {
    long stamp = lock.writeLock();
    try {
      V value = map.remove(key);
      if (value != null && !listeners.isEmpty()) {
        notifyRemoval(key, value, RemovalCause.EXPLICIT);
      }
      return value;
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public void clear() {
    long stamp = lock.writeLock();
    try {
      // 在写锁内直接遍历 map，避免调用 forEach（会触发 keySet/getIfPresent 导致重入死锁）
      for (Map.Entry<K, V> entry : map.entrySet()) {
        notifyRemoval(entry.getKey(), entry.getValue(), RemovalCause.EXPLICIT);
      }
      map.clear();
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public long estimatedSize() {
    long stamp = lock.readLock();
    try {
      return map.size();
    } finally {
      lock.unlockRead(stamp);
    }
  }

  @Override
  public boolean containsKey(K key) {
    long stamp = lock.readLock();
    try {
      return map.containsKey(key);
    } finally {
      lock.unlockRead(stamp);
    }
  }

  @Override
  public Set<K> keySet() {
    long stamp = lock.readLock();
    try {
      return new LinkedHashSet<>(map.keySet());
    } finally {
      lock.unlockRead(stamp);
    }
  }

  @Override
  public Collection<V> values() {
    long stamp = lock.readLock();
    try {
      return new ArrayList<>(map.values());
    } finally {
      lock.unlockRead(stamp);
    }
  }
}
