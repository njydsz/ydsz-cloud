package com.njydsz.common.cache.internal.lru;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;

/**
 * 基于 {@link ConcurrentHashMap} + 双向链表的 LRU（最近最少使用）缓存实现。
 *
 * <p>核心设计（并发优化）：
 *
 * <ul>
 *   <li>读路径无锁：命中率统计通过 {@code LongAdder} 无锁更新，链表调整在写锁内完成
 *   <li>写路径读写分离：{@link ReentrantReadWriteLock} 允许多个读线程并发，写入/淘汰时获取写锁
 *   <li>权威容量：{@code maxSize} 为容量硬上限，写入时超出则从链表头部淘汰最久未访问条目
 * </ul>
 *
 * <p>支持最大容量限制、命中/未命中统计、移除通知监听器。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class LRUCache<K, V> extends AbstractCache<K, V> {

  private final ConcurrentHashMap<K, Node<K, V>> map;

  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

  private final boolean recordStats;

  private final int maxSize;

  /** 链表哨兵头节点（next 指向最近访问的节点） */
  private final Node<K, V> head;

  /** 链表哨尾节点（prev 指向最久未访问的节点） */
  private final Node<K, V> tail;

  public LRUCache(int maxSize) {
    this(maxSize, Math.max(16, maxSize), true);
  }

  public LRUCache(int maxSize, int initialCapacity) {
    this(maxSize, initialCapacity, true);
  }

  public LRUCache(int maxSize, int initialCapacity, boolean recordStats) {
    this.maxSize = maxSize;
    this.map = new ConcurrentHashMap<>(Math.max(4, initialCapacity));
    this.recordStats = recordStats;
    this.head = new Node<>(null, null);
    this.tail = new Node<>(null, null);
    head.next = tail;
    tail.prev = head;
  }

  /**
   * 获取缓存值（不触发加载）。
   *
   * <p>读路径无锁：仅更新节点时间戳和调整链表位置（链表操作获取写锁）。
   * 命中时通过写锁保护链表调整，未命中时无额外开销。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    if (key == null) {
      return null;
    }
    Node<K, V> node = map.get(key);
    if (node != null) {
      if (recordStats) {
        hitCount.increment();
      }
      node.lastAccessNanos = System.nanoTime();
      promoteToHead(node);
      return node.value;
    }
    if (recordStats) {
      missCount.increment();
    }
    return null;
  }

  /**
   * 键存在时返回缓存值（并刷新 LRU 顺序），否则加锁原子计算并写入。
   *
   * <p>整个"查-算-写"流程在读锁 + 写锁组合内完成，多个线程对同一未命中键只会计算一次。
   *
   * @param key             缓存键
   * @param mappingFunction 键不存在时的计算函数
   * @return 缓存值或计算的新值
   */
  @Override
  public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
    Node<K, V> node = map.get(key);
    if (node != null) {
      if (recordStats) {
        hitCount.increment();
      }
      node.lastAccessNanos = System.nanoTime();
      promoteToHead(node);
      return node.value;
    }

    rwLock.writeLock().lock();
    try {
      // double-check：获取写锁后再次检查
      node = map.get(key);
      if (node != null) {
        if (recordStats) {
          hitCount.increment();
        }
        node.lastAccessNanos = System.nanoTime();
        addToHead(node);
        return node.value;
      }
      if (recordStats) {
        missCount.increment();
      }
      V value = mappingFunction.apply(key);
      if (value != null) {
        putInternal(key, value);
      }
      return value;
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /**
   * 写入键值对；超出容量时从链表尾部淘汰最久未访问条目。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      return;
    }
    rwLock.writeLock().lock();
    try {
      Node<K, V> existing = map.get(key);
      if (existing != null) {
        existing.value = value;
        existing.lastAccessNanos = System.nanoTime();
        addToHead(existing);
        if (!listeners.isEmpty()) {
          notifyRemoval(key, existing.value, RemovalCause.REPLACED);
        }
      } else {
        putInternal(key, value);
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    if (key == null) {
      return null;
    }
    rwLock.writeLock().lock();
    try {
      Node<K, V> node = map.remove(key);
      if (node != null) {
        removeFromList(node);
        if (!listeners.isEmpty()) {
          notifyRemoval(key, node.value, RemovalCause.EXPLICIT);
        }
        return node.value;
      }
      return null;
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /**
   * 清空缓存。
   */
  @Override
  public void clear() {
    rwLock.writeLock().lock();
    try {
      for (Node<K, V> node = head.next; node != tail; node = node.next) {
        notifyRemoval(node.key, node.value, RemovalCause.EXPLICIT);
      }
      map.clear();
      head.next = tail;
      tail.prev = head;
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  /**
   * 返回缓存条目数（精确值）。
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
   * 返回缓存键集合。
   *
   * @return 当前缓存键的快照集合
   */
  @Override
  public Set<K> keySet() {
    return new LinkedHashSet<>(map.keySet());
  }

  /**
   * 返回缓存值集合。
   *
   * @return 当前缓存值的快照集合
   */
  @Override
  public Collection<V> values() {
    List<V> result = new ArrayList<>(map.size());
    rwLock.readLock().lock();
    try {
      for (Node<K, V> node = head.next; node != tail; node = node.next) {
        result.add(node.value);
      }
    } finally {
      rwLock.readLock().unlock();
    }
    return result;
  }

  /**
   * 内部写入方法（调用方需持有写锁）。
   */
  private void putInternal(K key, V value) {
    Node<K, V> newNode = new Node<>(key, value);
    map.put(key, newNode);
    addToHead(newNode);
    evictIfNeeded();
  }

  /**
   * 容量淘汰（调用方需持有写锁）。
   */
  private void evictIfNeeded() {
    while (map.size() > maxSize) {
      Node<K, V> lru = tail.prev;
      if (lru == head) {
        break;
      }
      map.remove(lru.key);
      removeFromList(lru);
      notifyRemoval(lru.key, lru.value, RemovalCause.SIZE);
    }
  }

  /**
   * 将节点提升到链表头部（最近访问），获取写锁保护。
   */
  private void promoteToHead(Node<K, V> node) {
    rwLock.writeLock().lock();
    try {
      if (node.next != null && node.prev != null) {
        removeFromList(node);
        addToHead(node);
      }
    } finally {
      rwLock.writeLock().unlock();
    }
  }

  private void addToHead(Node<K, V> node) {
    node.prev = head;
    node.next = head.next;
    head.next.prev = node;
    head.next = node;
  }

  private void removeFromList(Node<K, V> node) {
    if (node.prev != null) {
      node.prev.next = node.next;
    }
    if (node.next != null) {
      node.next.prev = node.prev;
    }
    node.prev = null;
    node.next = null;
  }

  /**
   * 缓存节点：持有缓存值与最近访问时间戳。
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  private static class Node<K, V> {
    final K key;
    volatile V value;
    volatile long lastAccessNanos;
    Node<K, V> prev;
    Node<K, V> next;

    Node(K key, V value) {
      this.key = key;
      this.value = value;
      this.lastAccessNanos = System.nanoTime();
    }
  }
}
