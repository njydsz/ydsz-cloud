package com.njydsz.pmis.common.cache.internal.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.internal.AbstractCache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.cache.stats.CacheStats;

/**
 * 分段锁高性能缓存（无锁读取优化版）
 *
 * <p>核心优化：
 *
 * <ol>
 *   <li>读操作完全无锁：直接使用 ConcurrentHashMap.get()
 *   <li>写操作仅在必要时加锁：仅在容量满需要淘汰时获取锁
 *   <li>原子替换：使用 ConcurrentHashMap.replace() 实现 CAS 语义
 *   <li>批量淘汰：每次淘汰多个节点，减少锁竞争
 * </ol>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @since 1.0.0
 * 
 */
public class StripedConcurrentCache<K, V> extends AbstractCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(StripedConcurrentCache.class);

  private static final int DEFAULT_STRIPES = 32;
  private static final float LOAD_FACTOR = 0.75f;
  private static final int EVICT_BATCH_SIZE = 16;

  private final List<Segment<K, V>> segments;
  private final int mask;
  private final int maxSize;

  private final AtomicLong totalSize = new AtomicLong(0);

  public StripedConcurrentCache(int maxCapacity) {
    this(maxCapacity, DEFAULT_STRIPES);
  }

  public StripedConcurrentCache(int maxCapacity, int stripes) {
    int actualStripes = Integer.highestOneBit(Math.max(2, stripes) - 1) << 1;
    this.mask = actualStripes - 1;
    this.maxSize = maxCapacity;

    List<Segment<K, V>> segs = new ArrayList<>(actualStripes);
    this.segments = segs;

    int perSegmentCapacity = (int) ((maxCapacity / (float) actualStripes) / LOAD_FACTOR);
    perSegmentCapacity = Math.max(16, perSegmentCapacity);

    for (int i = 0; i < actualStripes; i++) {
      segs.add(new Segment<>(perSegmentCapacity, this));
    }

    log.info(
        "分段锁高性能缓存已创建，maxCapacity={}, stripes={}, perSegmentCapacity={}",
        maxCapacity,
        actualStripes,
        perSegmentCapacity);
  }

  @Override
  public V getIfPresent(K key) {
    if (key == null) {
      return null;
    }
    int segmentIndex = getSegmentIndex(key);
    V value = segments.get(segmentIndex).get(key);
    if (value == null) {
      missCount.increment();
    } else {
      hitCount.increment();
    }
    return value;
  }

  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      return;
    }
    int segmentIndex = getSegmentIndex(key);
    segments.get(segmentIndex).put(key, value);
  }

  @Override
  public V remove(K key) {
    if (key == null) {
      return null;
    }
    int segmentIndex = getSegmentIndex(key);
    return segments.get(segmentIndex).remove(key);
  }

  @Override
  public void clear() {
    for (Segment<K, V> segment : segments) {
      segment.clear();
    }
  }

  @Override
  public long estimatedSize() {
    return totalSize.get();
  }

  @Override
  public boolean containsKey(K key) {
    if (key == null) {
      return false;
    }
    int segmentIndex = getSegmentIndex(key);
    return segments.get(segmentIndex).containsKey(key);
  }

  @Override
  public Set<K> keySet() {
    Set<K> keys = new HashSet<>();
    for (Segment<K, V> segment : segments) {
      keys.addAll(segment.keySet());
    }
    return keys;
  }

  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>();
    for (Segment<K, V> segment : segments) {
      values.addAll(segment.values());
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

  private int getSegmentIndex(K key) {
    int hash = spread(key.hashCode());
    return hash & mask;
  }

  private int spread(int h) {
    return (h ^ (h >>> 16)) & 0x7FFFFFFF;
  }

  void incrementSize() {
    totalSize.incrementAndGet();
  }

  void decrementSize() {
    totalSize.decrementAndGet();
  }

  void decrementSize(int count) {
    totalSize.addAndGet(-count);
  }

  int getMaxSize() {
    return maxSize;
  }

  protected void notifyRemoval(K key, V value, RemovalCause cause) {
    super.notifyRemoval(key, value, cause);
  }

  private static class Segment<K, V> {

    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final ReentrantLock evictLock;
    private final StripedConcurrentCache<K, V> parent;
    private final int evictThreshold;

    private final AtomicInteger size = new AtomicInteger(0);

    Segment(int capacity, StripedConcurrentCache<K, V> parent) {
      this.evictThreshold = Math.max(1, (int) (capacity * 0.9f));
      this.parent = parent;
      this.map = new ConcurrentHashMap<>(capacity);
      this.evictLock = new ReentrantLock(false);
    }

    V get(K key) {
      Node<K, V> node = map.get(key);
      if (node != null) {
        // 更新访问时间（LRU 语义）
        node.lastAccessNanos = System.nanoTime();
        return node.value;
      }
      return null;
    }

    void put(K key, V value) {
      Node<K, V> newNode = new Node<>(key, value);
      Node<K, V> existing = map.putIfAbsent(key, newNode);
      if (existing == null) {
        size.incrementAndGet();
        parent.incrementSize();
        if (size.get() >= evictThreshold) {
          evictLock.lock();
          try {
            if (size.get() >= evictThreshold) {
              evict(EVICT_BATCH_SIZE);
            }
          } finally {
            evictLock.unlock();
          }
        }
        return;
      }
      // Atomic value update using volatile write
      existing.value = value;
      existing.lastAccessNanos = System.nanoTime();
    }

    V remove(K key) {
      Node<K, V> node = map.remove(key);
      if (node != null) {
        size.decrementAndGet();
        parent.decrementSize();
        return node.value;
      }
      return null;
    }

    void clear() {
      evictLock.lock();
      try {
        for (Map.Entry<K, Node<K, V>> entry : map.entrySet()) {
          parent.notifyRemoval(entry.getKey(), entry.getValue().value, RemovalCause.EXPLICIT);
        }
        int removed = size.get();
        map.clear();
        size.set(0);
        if (removed > 0) {
          parent.decrementSize(removed);
        }
      } finally {
        evictLock.unlock();
      }
    }

    boolean containsKey(K key) {
      return map.containsKey(key);
    }

    Set<K> keySet() {
      return map.keySet();
    }

    Collection<V> values() {
      List<V> result = new ArrayList<>();
      for (Node<K, V> node : map.values()) {
        result.add(node.value);
      }
      return result;
    }

    private void evict(int batchSize) {
      // LRU 淘汰：按访问时间排序，淘汰最旧的条目
      List<Map.Entry<K, Node<K, V>>> entries =
          new ArrayList<>(map.entrySet());
      entries.sort(
          (a, b) -> Long.compare(a.getValue().lastAccessNanos, b.getValue().lastAccessNanos));
      int evictCount = Math.min(batchSize, entries.size());
      for (int i = 0; i < evictCount; i++) {
        Map.Entry<K, Node<K, V>> entry = entries.get(i);
        K key = entry.getKey();
        Node<K, V> node = entry.getValue();
        // 使用 remove 确保 CAS 语义，避免并发删除
        if (map.remove(key, node)) {
          size.decrementAndGet();
          parent.decrementSize();
          parent.notifyRemoval(key, node.value, RemovalCause.SIZE);
        }
      }
    }
  }

  private static class Node<K, V> {
    volatile V value;
    volatile long lastAccessNanos;

    Node(K key, V value) {
      this.value = value;
      this.lastAccessNanos = System.nanoTime();
    }
  }
}
