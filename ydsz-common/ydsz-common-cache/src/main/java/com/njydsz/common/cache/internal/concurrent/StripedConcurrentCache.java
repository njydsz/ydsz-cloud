package com.njydsz.common.cache.internal.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.stats.CacheStats;

/**
 * 分段锁高性能缓存（采样 LRU 淘汰版）。
 *
 * <p>核心设计（并发安全修正）：
 *
 * <ol>
 *   <li>读路径无锁：命中仅更新 {@code lastAccessNanos}（volatile 写），不触碰链表
 *   <li>链表独占：所有链表修改（addToTail / removeFromList / moveToTail / 淘汰采样）均在
 *       {@code evictLock} 内完成，消除原实现 get/put/remove 无锁改链表的竞争
 *   <li>采样 LRU 淘汰：淘汰时从链表头部向后采样 {@link #EVICT_SAMPLE_SIZE} 个候选，
 *       选择最近访问时间最早的条目淘汰，正确性优先、读路径零锁
 *   <li>权威容量：{@code maximumSize} 为全局总容量硬上限，段内达到阈值或全局超限均触发淘汰，
 *       运行时缩容（{@code policy().eviction().setMaximum}）立即同步收缩
 * </ol>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class StripedConcurrentCache<K, V> extends AbstractCache<K, V> {

  private static final Logger log = LoggerFactory.getLogger(StripedConcurrentCache.class);

  /** 默认分段数 */
  private static final int DEFAULT_STRIPES = 32;

  /** 单次淘汰的最大条目数 */
  private static final int EVICT_BATCH_SIZE = 16;

  /** 淘汰候选采样数量 */
  private static final int EVICT_SAMPLE_SIZE = 8;

  private final List<Segment<K, V>> segments;
  private final int mask;

  /** 全局权威总容量（volatile，支持运行时动态调整） */
  private volatile int maxSize;

  /** 全局条目计数 */
  private final AtomicLong totalSize = new AtomicLong(0);

  /**
   * 创建分段缓存。
   *
   * @param maxCapacity 最大容量
   */
  public StripedConcurrentCache(int maxCapacity) {
    this(maxCapacity, DEFAULT_STRIPES);
  }

  /**
   * 创建分段缓存。
   *
   * @param maxCapacity 最大容量
   * @param stripes 分段数（向上取 2 的幂）
   */
  public StripedConcurrentCache(int maxCapacity, int stripes) {
    int actualStripes = Integer.highestOneBit(Math.max(2, stripes) - 1) << 1;
    this.mask = actualStripes - 1;
    this.maxSize = Math.max(1, maxCapacity);

    List<Segment<K, V>> segs = new ArrayList<>(actualStripes);
    this.segments = segs;

    int perSegmentCapacity = Math.max(1, this.maxSize / actualStripes);

    for (int i = 0; i < actualStripes; i++) {
      segs.add(new Segment<>(perSegmentCapacity, this));
    }

    log.info(
        "分段锁缓存已创建，maxSize={}, stripes={}, perSegmentCapacity={}",
        this.maxSize,
        actualStripes,
        perSegmentCapacity);
  }

  /**
   * 获取缓存值（不触发加载），命中时无锁更新访问时间戳。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
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

  /**
   * 写入键值对；新增条目后按段内阈值或全局容量上限触发采样 LRU 淘汰。
   *
   * @param key 缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      return;
    }
    int segmentIndex = getSegmentIndex(key);
    segments.get(segmentIndex).put(key, value);
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
    int segmentIndex = getSegmentIndex(key);
    return segments.get(segmentIndex).remove(key);
  }

  /**
   * 清空全部分段。
   */
  @Override
  public void clear() {
    for (final Segment<K, V> segment : segments) {
      segment.clear();
    }
  }

  /**
   * 返回缓存条目总数。
   *
   * @return 缓存条目总数
   */
  @Override
  public long estimatedSize() {
    return totalSize.get();
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * @param key 缓存键
   * @return 键存在时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    if (key == null) {
      return false;
    }
    int segmentIndex = getSegmentIndex(key);
    return segments.get(segmentIndex).containsKey(key);
  }

  /**
   * 返回缓存键集合（一次性快照）。
   *
   * @return 当前缓存所有键的快照集合
   */
  @Override
  public Set<K> keySet() {
    Set<K> keys = new HashSet<>();
    for (final Segment<K, V> segment : segments) {
      keys.addAll(segment.keySet());
    }
    return keys;
  }

  /**
   * 返回缓存值集合（一次性快照）。
   *
   * @return 当前缓存所有值的快照集合
   */
  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>();
    for (final Segment<K, V> segment : segments) {
      values.addAll(segment.values());
    }
    return values;
  }

  /**
   * 获取缓存命中率。
   *
   * @return 命中率，范围 [0.0, 1.0]
   */
  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  /**
   * 获取缓存统计快照。
   *
   * @return 包含命中数与未命中数的统计对象
   */
  @Override
  public CacheStats getStats() {
    return new CacheStats(hitCount.sum(), missCount.sum());
  }

  /**
   * 获取缓存策略查询接口 — 支持运行时调整最大容量（缩容立即生效）。
   *
   * @return 缓存策略
   */
  @Override
  public CachePolicy policy() {
    return new CachePolicy() {
      @Override
      public Optional<EvictionPolicy> eviction() {
        return Optional.of(
            new EvictionPolicy() {
              @Override
              public OptionalLong getMaximum() {
                return OptionalLong.of(maxSize);
              }

              @Override
              public void setMaximum(long maximumSize) {
                if (maximumSize < 1) {
                  throw new IllegalArgumentException("maximumSize must be >= 1");
                }
                int oldMaxSize = maxSize;
                maxSize = (int) maximumSize;
                log.info("StripedConcurrentCache 最大容量调整: {} -> {}", oldMaxSize, maxSize);
                if (maxSize < totalSize.get()) {
                  shrinkToCapacity();
                }
              }

              @Override
              public OptionalLong weightedSize() {
                return OptionalLong.empty();
              }

              @Override
              public boolean isWeighted() {
                return false;
              }
            });
      }

      @Override
      public Optional<ExpirationPolicy> expiration() {
        return Optional.empty();
      }
    };
  }

  /**
   * 全局缩容：逐段在锁内淘汰至总量不超过当前容量。
   */
  private void shrinkToCapacity() {
    for (final Segment<K, V> segment : segments) {
      segment.shrinkToGlobalCapacity();
    }
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

  /** 将分段内的淘汰/显式删除事件向上透传。 */
  protected void notifyRemoval(K key, V value, RemovalCause cause) {
    super.notifyRemoval(key, value, cause);
  }

  /**
   * 分段内部实现：独立数据表 + 锁保护的采样 LRU 链表。
   *
   * <p>链表仅由 {@code evictLock} 保护；读路径只更新节点 {@code lastAccessNanos}。
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  private static class Segment<K, V> {

    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final ReentrantLock evictLock;
    private final StripedConcurrentCache<K, V> parent;
    private final int evictThreshold;

    private final Node<K, V> head;
    private final Node<K, V> tail;

    private final AtomicInteger size = new AtomicInteger(0);

    Segment(int capacity, StripedConcurrentCache<K, V> parent) {
      this.evictThreshold = Math.max(1, capacity);
      this.parent = parent;
      this.map = new ConcurrentHashMap<>(Math.max(4, capacity));
      this.evictLock = new ReentrantLock(false);
      this.head = new Node<>(null, null);
      this.tail = new Node<>(null, null);
      head.next = tail;
      tail.prev = head;
    }

    V get(K key) {
      Node<K, V> node = map.get(key);
      if (node != null) {
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
        evictLock.lock();
        try {
          addToTail(newNode);
        } finally {
          evictLock.unlock();
        }
        maybeEvict();
        return;
      }
      existing.value = value;
      existing.lastAccessNanos = System.nanoTime();
      evictLock.lock();
      try {
        moveToTail(existing);
      } finally {
        evictLock.unlock();
      }
    }

    V remove(K key) {
      Node<K, V> node = map.remove(key);
      if (node != null) {
        size.decrementAndGet();
        parent.decrementSize();
        evictLock.lock();
        try {
          removeFromList(node);
        } finally {
          evictLock.unlock();
        }
        return node.value;
      }
      return null;
    }

    void clear() {
      evictLock.lock();
      try {
        for (final Map.Entry<K, Node<K, V>> entry : map.entrySet()) {
          parent.notifyRemoval(entry.getKey(), entry.getValue().value, RemovalCause.EXPLICIT);
        }
        int removed = size.get();
        map.clear();
        size.set(0);
        if (removed > 0) {
          parent.decrementSize(removed);
        }
        head.next = tail;
        tail.prev = head;
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
      for (final Node<K, V> node : map.values()) {
        result.add(node.value);
      }
      return result;
    }

    /**
     * 按段内阈值或全局容量上限触发淘汰（锁内执行）。
     */
    private void maybeEvict() {
      if (size.get() < evictThreshold && parent.totalSize.get() < parent.maxSize) {
        return;
      }
      evictLock.lock();
      try {
        int evicted = 0;
        while (evicted < EVICT_BATCH_SIZE && size.get() > 0 && shouldEvict()) {
          if (evictOne()) {
            evicted++;
          }
        }
      } finally {
        evictLock.unlock();
      }
    }

    private boolean shouldEvict() {
      return size.get() >= evictThreshold || parent.totalSize.get() >= parent.maxSize;
    }

    /**
     * 采样 LRU 淘汰：从链表头部向后采样候选，淘汰最近访问时间最早的条目。
     *
     * @return 是否淘汰成功
     */
    private boolean evictOne() {
      Node<K, V> victim = null;
      long oldest = Long.MAX_VALUE;
      int sampled = 0;
      Node<K, V> current = head.next;
      while (current != tail && sampled < EVICT_SAMPLE_SIZE) {
        if (current.lastAccessNanos < oldest) {
          oldest = current.lastAccessNanos;
          victim = current;
        }
        sampled++;
        current = current.next;
      }
      if (victim == null || victim.key == null) {
        return false;
      }
      if (map.remove(victim.key, victim)) {
        removeFromList(victim);
        size.decrementAndGet();
        parent.decrementSize();
        parent.notifyRemoval(victim.key, victim.value, RemovalCause.SIZE);
        return true;
      }
      return false;
    }

    /**
     * 全局缩容：锁内持续淘汰直至总量不超过全局容量。
     */
    void shrinkToGlobalCapacity() {
      evictLock.lock();
      try {
        while (parent.totalSize.get() > parent.maxSize && size.get() > 0) {
          if (!evictOne()) {
            break;
          }
        }
      } finally {
        evictLock.unlock();
      }
    }

    private void addToTail(Node<K, V> node) {
      node.prev = tail.prev;
      node.next = tail;
      tail.prev.next = node;
      tail.prev = node;
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

    private void moveToTail(Node<K, V> node) {
      if (tail.prev == node) {
        return;
      }
      removeFromList(node);
      addToTail(node);
    }
  }

  /**
   * 缓存节点：持有缓存值与最近访问时间戳（volatile）。
   *
   * <p>链表指针仅在 {@code evictLock} 保护下修改。
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
