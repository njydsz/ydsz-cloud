package com.njydsz.pmis.common.cache.internal.tinylfu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.internal.AbstractCache;
import com.njydsz.pmis.common.cache.internal.lfu.FrequencySketch;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.stats.CacheStats;

public class WindowTinyLFUCache<K, V> extends AbstractCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(WindowTinyLFUCache.class);

  private final int maxSize;
  private final ConcurrentHashMap<K, Node<K, V>> data;
  private final Node<K, V> windowHead;
  private Node<K, V> windowTail;
  private final Node<K, V> probationHead;
  private Node<K, V> probationTail;
  private final Node<K, V> protectedHead;
  private Node<K, V> protectedTail;
  private final FrequencySketch frequencySketch;
  private final AtomicLong sizeCounter;
  private final AtomicLong protectedSize;
  private final ReentrantReadWriteLock rwLock;
  private final ReentrantReadWriteLock.WriteLock writeLock;
  private final int shiftThreshold;
  private volatile long totalCount;

  public WindowTinyLFUCache(int maxCapacity) {
    this(maxCapacity, 1);
  }

  public WindowTinyLFUCache(int maxCapacity, int stripes) {
    this.maxSize = maxCapacity;
    this.data = new ConcurrentHashMap<>(Math.max(16, maxCapacity / 2));
    this.windowHead = new Node<>(null, null, 0);
    this.windowTail = this.windowHead;
    this.probationHead = new Node<>(null, null, 1);
    this.probationTail = this.probationHead;
    this.protectedHead = new Node<>(null, null, 2);
    this.protectedTail = this.protectedHead;
    this.frequencySketch = new FrequencySketch();
    this.frequencySketch.ensureCapacity(maxCapacity);
    this.sizeCounter = new AtomicLong(0);
    this.protectedSize = new AtomicLong(0);
    this.rwLock = new ReentrantReadWriteLock(false);
    this.writeLock = rwLock.writeLock();
    this.totalCount = 0;
    this.shiftThreshold = Math.max(maxCapacity, 1000);
    log.info(
        "Window-TinyLFU 缓存已创建（Caffeine 架构，并发安全增强，周期性衰减机制），maxCapacity={}, shiftThreshold={}",
        maxCapacity,
        shiftThreshold);
  }

  @Override
  public V getIfPresent(K key) {
    if (key == null) {
      return null;
    }
    Node<K, V> node = data.get(key);
    if (node == null) {
      missCount.increment();
      return null;
    }
    frequencySketch.increment(key);
    if (node.queue == 1) {
      // 安全提升：在 writeLock 内重新校验 node 是否仍在 data 中且 queue 未变，
      // 避免并发 put 淘汰导致 node 已从链表移除后仍操作野指针
      safeMoveToProtected(node, key);
    }
    hitCount.increment();
    return node.value;
  }

  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      return;
    }
    Node<K, V> existing = data.get(key);
    if (existing != null) {
      existing.value = value;
      frequencySketch.increment(key);
      return;
    }
    writeLock.lock();
    try {
      Node<K, V> doubleCheck = data.get(key);
      if (doubleCheck != null) {
        doubleCheck.value = value;
        frequencySketch.increment(key);
        return;
      }
      long currentTotal = totalCount;
      if (currentTotal >= shiftThreshold) {
        frequencySketch.reset();
        totalCount = 0;
      }
      totalCount++;
      while (sizeCounter.get() >= maxSize) {
        if (!evictOnce()) {
          break;
        }
      }
      Node<K, V> newNode = new Node<>(key, value, 0);
      data.put(key, newNode);
      addFirst(windowHead, newNode);
      sizeCounter.incrementAndGet();
      frequencySketch.increment(key);
    } finally {
      writeLock.unlock();
    }
  }

  private boolean evictOnce() {
    Node<K, V> victim = removeLast(windowHead);
    if (victim != null) {
      data.remove(victim.key);
      sizeCounter.decrementAndGet();
      notifyRemoval(victim.key, victim.value, RemovalCause.SIZE);
      return true;
    }
    victim = removeLast(probationHead);
    if (victim != null) {
      if (!isEmpty(protectedHead)) {
        Node<K, V> protectedFirst = removeFirst(protectedHead);
        if (protectedFirst != null) {
          protectedSize.decrementAndGet();
          if (frequencySketch.frequency(victim.key)
              < frequencySketch.frequency(protectedFirst.key)) {
            protectedFirst.queue = 1;
            addFirst(probationHead, protectedFirst);
            data.remove(victim.key);
            sizeCounter.decrementAndGet();
            notifyRemoval(victim.key, victim.value, RemovalCause.SIZE);
            return true;
          } else {
            data.remove(protectedFirst.key);
            sizeCounter.decrementAndGet();
            notifyRemoval(protectedFirst.key, protectedFirst.value, RemovalCause.SIZE);
            addFirst(probationHead, victim);
            return true;
          }
        }
      }
      data.remove(victim.key);
      sizeCounter.decrementAndGet();
      notifyRemoval(victim.key, victim.value, RemovalCause.SIZE);
      return true;
    }
    return false;
  }

  /**
   * 安全提升到 Protected 队列 — 在 writeLock 内重新校验 node 有效性
   *
   * <p>防止并发淘汰场景下操作已从链表移除的 Node（野指针）。
   *
   * @param node 待提升的节点
   * @param key 缓存键（用于重新校验 data 中是否仍存在）
   */
  private void safeMoveToProtected(Node<K, V> node, K key) {
    writeLock.lock();
    try {
      // 重新校验：node 仍在 data 中且仍为 probation 队列
      Node<K, V> current = data.get(key);
      if (current != node || current.queue != 1) {
        // node 已被并发淘汰或队列已变更，跳过提升
        return;
      }
      doMoveToProtected(node);
    } finally {
      writeLock.unlock();
    }
  }

  private void moveToProtected(Node<K, V> node) {
    writeLock.lock();
    try {
      if (node.queue != 1) {
        return;
      }
      doMoveToProtected(node);
    } finally {
      writeLock.unlock();
    }
  }

  private void doMoveToProtected(Node<K, V> node) {
    remove(node);
    node.queue = 2;
    addFirst(protectedHead, node);
    long pSize = protectedSize.incrementAndGet();
    if (pSize > maxSize * 0.80) {
      Node<K, V> demoted = removeLast(protectedHead);
      if (demoted != null) {
        demoted.queue = 1;
        addFirst(probationHead, demoted);
        protectedSize.decrementAndGet();
      }
    }
  }

  @Override
  public V remove(K key) {
    if (key == null) {
      return null;
    }
    writeLock.lock();
    try {
      Node<K, V> node = data.remove(key);
      if (node != null) {
        remove(node);
        sizeCounter.decrementAndGet();
        notifyRemoval(node.key, node.value, RemovalCause.EXPLICIT);
        return node.value;
      }
      return null;
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void clear() {
    writeLock.lock();
    try {
      for (Node<K, V> node : data.values()) {
        notifyRemoval(node.key, node.value, RemovalCause.EXPLICIT);
      }
      data.clear();
      clearAll(windowHead);
      clearAll(probationHead);
      clearAll(protectedHead);
      sizeCounter.set(0);
      protectedSize.set(0);
    } finally {
      writeLock.unlock();
    }
  }

  private void addFirst(Node<K, V> head, Node<K, V> node) {
    node.prev = head;
    node.next = head.next;
    if (head.next != null) {
      head.next.prev = node;
    } else {
      // 列表之前为空，更新 tail
      updateTail(head, null, node);
    }
    head.next = node;
  }

  /** 更新指定头节点对应的 tail 指针 */
  private void updateTail(Node<K, V> head, Node<K, V> expectedTail, Node<K, V> newTail) {
    if (head == windowHead) {
      windowTail = newTail;
    } else if (head == probationHead) {
      probationTail = newTail;
    } else if (head == protectedHead) {
      protectedTail = newTail;
    }
  }

  private void remove(Node<K, V> node) {
    if (node.prev != null) {
      node.prev.next = node.next;
    }
    if (node.next != null) {
      node.next.prev = node.prev;
    }
    // 更新 tail 指针（如果删除的是尾节点）
    if (node == windowTail && node.prev != windowHead) {
      windowTail = node.prev;
    } else if (node == windowTail && node.prev == windowHead) {
      windowTail = windowHead;
    }
    if (node == probationTail && node.prev != probationHead) {
      probationTail = node.prev;
    } else if (node == probationTail && node.prev == probationHead) {
      probationTail = probationHead;
    }
    if (node == protectedTail && node.prev != protectedHead) {
      protectedTail = node.prev;
    } else if (node == protectedTail && node.prev == protectedHead) {
      protectedTail = protectedHead;
    }
    node.prev = null;
    node.next = null;
  }

  private Node<K, V> removeFirst(Node<K, V> head) {
    Node<K, V> first = head.next;
    if (first != null) {
      head.next = first.next;
      if (first.next != null) {
        first.next.prev = head;
      } else {
        // 列表变空，重置 tail
        updateTail(head, null, head);
      }
      first.prev = null;
      first.next = null;
    }
    return first;
  }

  /** O(1) 尾删除（使用 tail 指针） */
  private Node<K, V> removeLast(Node<K, V> head) {
    Node<K, V> tail = getTail(head);
    if (tail == head || tail == null) {
      return null;
    }
    // 从链表中移除尾节点
    tail.prev.next = null;
    Node<K, V> newTail = (tail.prev == head) ? head : tail.prev;
    updateTail(head, tail, newTail);
    tail.prev = null;
    tail.next = null;
    return tail;
  }

  /** 获取指定头节点对应的 tail 指针 */
  private Node<K, V> getTail(Node<K, V> head) {
    if (head == windowHead) return windowTail;
    if (head == probationHead) return probationTail;
    if (head == protectedHead) return protectedTail;
    return null;
  }

  private boolean isEmpty(Node<K, V> head) {
    return head.next == null;
  }

  private void clearAll(Node<K, V> head) {
    head.next = null;
    updateTail(head, null, head);
  }

  @Override
  public long estimatedSize() {
    return sizeCounter.get();
  }

  @Override
  public boolean containsKey(K key) {
    if (key == null) {
      return false;
    }
    return data.containsKey(key);
  }

  @Override
  public Set<K> keySet() {
    return new HashSet<>(data.keySet());
  }

  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>(data.size());
    for (Node<K, V> node : data.values()) {
      values.add(node.value);
    }
    return values;
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

  private static final class Node<K, V> {
    final K key;
    volatile V value;
    volatile int queue;
    volatile Node<K, V> prev;
    volatile Node<K, V> next;

    Node(K key, V value, int queue) {
      this.key = key;
      this.value = value;
      this.queue = queue;
    }
  }
}
