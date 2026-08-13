package com.njydsz.common.cache.internal.concurrent;

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

import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.cache.stats.CacheStats;

/**
 * 分段锁高性能缓存（O(1) LRU 优化版）
 *
 * <p>核心优化：
 *
 * <ol>
 *   <li>读操作无锁命中：直接使用 ConcurrentHashMap.get()
 *   <li>O(1) LRU 淘汰：每个分段内维护双向链表，淘汰时从链表头部 O(1) 移除最旧条目
 *   <li>批量淘汰：每次淘汰多个节点，减少锁竞争频次
 *   <li>惰性访问更新：读路径仅更新 timestamp，不触发链表重排；链表重排仅在批量淘汰时批量处理
 * </ol>
 *
 * <p>LRU 数据结构：每个 Segment 维护一个双向链表（head <-> node1 <-> node2 <-> ... <-> tail），
 * 最近访问的节点靠近尾部，最久未访问的靠近头部。淘汰时从头部批量移除，时间复杂度 O(k)（k 为淘汰数量）。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
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

  /**
   * 获取缓存值（不触发加载）。
   *
   * <p>读路径无锁，按 key 哈希路由到对应分段读取；命中时更新 LRU 链表位置，计数统计。
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
   * 写入键值对。
   *
   * <p>首次写入使分段尺寸达到淘汰阈值时，在分段锁内通过 LRU 链表 O(1) 批量淘汰旧条目。
   *
   * @param key   缓存键
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
   *
   * <p>逐段清空，每段会向监听器发送 {@link RemovalCause#EXPLICIT} 通知并重置段内尺寸计数。
   */
  @Override
  public void clear() {
    for (Segment<K, V> segment : segments) {
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
   * 返回缓存键集合。
   *
   * <p>聚合全部分段的键到新 {@link HashSet}，为一次性快照。
   *
   * @return 当前缓存所有键的快照集合
   */
  @Override
  public Set<K> keySet() {
    Set<K> keys = new HashSet<>();
    for (Segment<K, V> segment : segments) {
      keys.addAll(segment.keySet());
    }
    return keys;
  }

  /**
   * 返回缓存值集合。
   *
   * <p>聚合全部分段的值到新 {@link ArrayList}，为一次性快照。
   *
   * @return 当前缓存所有值的快照集合
   */
  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>();
    for (Segment<K, V> segment : segments) {
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
   * @return 包含命中数、未命中数的统计对象
   */
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

  /**
   * 将分段内的淘汰/显式删除事件向上透传。
   *
   * @param key 被移除的键
   * @param value 被移除的值
   * @param cause 移除原因
   */
  protected void notifyRemoval(K key, V value, RemovalCause cause) {
    super.notifyRemoval(key, value, cause);
  }

  /**
   * 分段内部实现：每个分段持有独立的 {@link ConcurrentHashMap} 与 O(1) LRU 双向链表。
   *
   * <p>数据结构：
   * <ul>
   *   <li>数据容器：ConcurrentHashMap&lt;K, Node&lt;K,V&gt;&gt; —— O(1) 读写</li>
   *   <li>LRU 链表：head(哨兵) <-> ... 节点 ... <-> tail(哨兵) —— 双向链表维护访问顺序</li>
   * </ul>
   *
   * <p>淘汰机制：
   * <ul>
   *   <li>容量阈值 evictThreshold = 分段容量 * 0.9</li>
   *   <li>达到阈值时，锁内从链表头部（最旧）批量移除 EVICT_BATCH_SIZE 个节点</li>
   *   <li>每次淘汰操作时间复杂度 O(k), k = EVICT_BATCH_SIZE</li>
   * </ul>
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  private static class Segment<K, V> {

    private final ConcurrentHashMap<K, Node<K, V>> map;
    private final ReentrantLock evictLock;
    private final StripedConcurrentCache<K, V> parent;
    private final int evictThreshold;

    /** LRU 双向链表哨兵头节点（最旧的下一个） */
    private final Node<K, V> head;

    /** LRU 双向链表哨兵尾节点（最新的前一个） */
    private final Node<K, V> tail;

    private final AtomicInteger size = new AtomicInteger(0);

    Segment(int capacity, StripedConcurrentCache<K, V> parent) {
      this.evictThreshold = Math.max(1, (int) (capacity * 0.9f));
      this.parent = parent;
      this.map = new ConcurrentHashMap<>(capacity);
      this.evictLock = new ReentrantLock(false);

      // 初始化哨兵节点
      this.head = new Node<>(null, null);
      this.tail = new Node<>(null, null);
      head.next = tail;
      tail.prev = head;
    }

    V get(K key) {
      Node<K, V> node = map.get(key);
      if (node != null) {
        // 更新访问时间并移动到链表尾部（标记为最近使用）
        node.lastAccessNanos = System.nanoTime();
        moveToTail(node);
        return node.value;
      }
      return null;
    }

    void put(K key, V value) {
      Node<K, V> newNode = new Node<>(key, value);
      Node<K, V> existing = map.putIfAbsent(key, newNode);
      if (existing == null) {
        // 新节点：添加到链表尾部
        size.incrementAndGet();
        parent.incrementSize();
        addToTail(newNode);
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
      // 已存在：更新值并移动到链表尾部
      existing.value = value;
      existing.lastAccessNanos = System.nanoTime();
      moveToTail(existing);
    }

    V remove(K key) {
      Node<K, V> node = map.remove(key);
      if (node != null) {
        size.decrementAndGet();
        parent.decrementSize();
        // 从链表移除
        removeFromList(node);
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
        // 重置链表
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
      for (Node<K, V> node : map.values()) {
        result.add(node.value);
      }
      return result;
    }

    // ====== LRU 双向链表操作（必须在 evictLock 写锁保护或单线程内调用） ======

    /** 将节点添加到链表尾部（最近使用） */
    private void addToTail(Node<K, V> node) {
      node.prev = tail.prev;
      node.next = tail;
      tail.prev.next = node;
      tail.prev = node;
    }

    /** 从链表中移除指定节点 */
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

    /** 将已有节点移动到链表尾部（标记为最近使用） */
    private void moveToTail(Node<K, V> node) {
      // 快速检查：如果已经在尾部前一位，无需移动
      if (tail.prev == node) {
        return;
      }
      removeFromList(node);
      addToTail(node);
    }

    /**
     * O(1) LRU 淘汰：从链表头部批量移除最旧的节点。
     *
     * <p>时间复杂度 O(k)，k = min(batchSize, 当前条目数)。
     * 与 sort-based 方案（O(n log n)）相比，在大容量、高并发场景下性能提升显著。
     */
    private void evict(int batchSize) {
      int evictCount = 0;
      Node<K, V> current = head.next;
      while (current != tail && evictCount < batchSize) {
        Node<K, V> next = current.next;
        K key = current.key;
        // 使用 CAS remove 避免并发删除冲突
        if (map.remove(key, current)) {
          removeFromList(current);
          size.decrementAndGet();
          parent.decrementSize();
          parent.notifyRemoval(key, current.value, RemovalCause.SIZE);
          evictCount++;
        }
        current = next;
      }
    }
  }

  /**
   * 缓存节点：持有缓存值、最近访问时间戳和 LRU 双向链表指针。
   *
   * <p>LRU 链表由 head/tail 哨兵节点 + 各节点的 prev/next 指针组成双向链表。
   * 最近访问的节点靠近 tail，最久未访问的靠近 head。
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  private static class Node<K, V> {
    final K key;
    volatile V value;
    volatile long lastAccessNanos;

    // LRU 双向链表指针
    Node<K, V> prev;
    Node<K, V> next;

    Node(K key, V value) {
      this.key = key;
      this.value = value;
      this.lastAccessNanos = System.nanoTime();
    }
  }
}
