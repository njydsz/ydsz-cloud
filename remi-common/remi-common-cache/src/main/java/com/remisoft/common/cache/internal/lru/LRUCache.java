package com.remisoft.common.cache.internal.lru;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

import com.remisoft.common.cache.internal.AbstractCache;
import com.remisoft.common.cache.listener.RemovalCause;

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
 * @author remi-team
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
          /**
           * LRU 容量上限控制：当条目数超过 maxSize 时淘汰最久未访问的条目。
           *
           * <p>被淘汰的条目不在此处直接通知监听器（避免在 LinkedHashMap 内部回调中触发
           * 重入操作），而是压入 {@link #pendingEvictions} 线程本地队列，由外层方法在
           * 释放写锁后统一 {@link #drainEvictions()} 处理。
           *
           * @param eldest 最久未访问的条目
           * @return 条目数超过 maxSize 时返回 {@code true} 触发淘汰
           */
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

  /**
   * 获取缓存值（不触发加载），并维护 LRU 访问顺序。
   *
   * <p>access-order 模式下读操作会调整内部链表结构，因此必须持有写锁（不能走乐观读）；
   * 命中/未命中计入统计（未开启统计时跳过）。由于锁持有期间可能触发容量淘汰，
   * 锁释放后统一处理延迟淘汰通知。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
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

  /**
   * 键存在时返回缓存值（并刷新 LRU 顺序），否则加锁原子计算并写入。
   *
   * <p>整个"查-算-写"流程在写锁内完成，多个线程对同一未命中键只会计算一次；
   * 计算结果为 null 时不写入缓存。同样通过写锁后延迟队列处理容量淘汰通知。
   *
   * @param key             缓存键
   * @param mappingFunction 键不存在时的计算函数
   * @return 缓存值或计算的新值
   */
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

  /**
   * 写入键值对；键已存在时覆盖旧值并向监听器发送 {@link RemovalCause#REPLACED} 通知。
   *
   * <p>写入可能触发 LRU 容量淘汰（最久未使用条目），淘汰通知延迟到锁释放后处理。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
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

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>成功移除（旧值非 null）且存在监听器时发送 {@link RemovalCause#EXPLICIT} 通知。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
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

  /**
   * 清空缓存。
   *
   * <p>在写锁内直接遍历底层 Map 发送 {@link RemovalCause#EXPLICIT} 通知，
   * 刻意不走 {@code forEach} 路径，避免经 keySet/getIfPresent 造成锁重入死锁。
   *
   */
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

  /**
   * 返回缓存条目数（精确值，读锁保护下统计）。
   *
   * @return 缓存条目数
   */
  @Override
  public long estimatedSize() {
    long stamp = lock.readLock();
    try {
      return map.size();
    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * @param key 缓存键
   * @return 键存在时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    long stamp = lock.readLock();
    try {
      return map.containsKey(key);
    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * 返回缓存键集合。
   *
   * <p>读锁内复制为新的 {@link LinkedHashSet}（保留访问序），返回一次性快照， 与底层 Map 的弱一致视图不同。
   *
   * @return 当前缓存键的有序快照集合
   */
  @Override
  public Set<K> keySet() {
    long stamp = lock.readLock();
    try {
      return new LinkedHashSet<>(map.keySet());
    } finally {
      lock.unlockRead(stamp);
    }
  }

  /**
   * 返回缓存值集合。
   *
   * <p>读锁内复制为新的 {@link ArrayList}，返回一次性快照，顺序与底层 Map 迭代序一致。
   *
   * @return 当前缓存值的快照集合
   */
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
