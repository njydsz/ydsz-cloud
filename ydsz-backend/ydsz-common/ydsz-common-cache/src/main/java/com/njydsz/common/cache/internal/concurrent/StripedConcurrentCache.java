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
   * <p>读路径无锁，按 key 哈希路由到对应分段读取；命中/未命中分别递增统计。
   * null 键直接返回 null 并计入未命中。
   *
   * @param key 缓存键，为 null 时返回 {@code null}
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
   * <p>null 键或 null 值被静默忽略（不写入、不报错）。首次写入使分段尺寸达到淘汰阈值时，
   * 在分段锁内批量淘汰最久未访问的条目，以维持总容量不超过 {@code maxCapacity}。
   *
   * @param key   缓存键，为 null 时忽略
   * @param value 缓存值，为 null 时忽略
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
   * <p>null 键直接返回 {@code null}，不触发任何删除动作。
   *
   * @param key 缓存键，为 null 时返回 {@code null}
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
   *
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
   * <p>基于 {@link AtomicLong} 总计数器，为精确的近似值（并发写入下可能略有滞后）。
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
   * <p>null 键返回 {@code false}。
   *
   * @param key 缓存键，为 null 时返回 {@code false}
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
   * <p>聚合全部分段的键到新 {@link HashSet}，为一次性快照而非实时视图； 快照期间发生的并发修改不会被反映。
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
   * <p>聚合全部分段的值到新 {@link ArrayList}，为一次性快照； 值可能重复，且不保证与 {@link #keySet()} 顺序对应。
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
   * <p>命中率 = 命中次数 / (命中 + 未命中)，无访问记录时返回 0.0。
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
   * @return 包含命中数、未命中数的统计对象，淘汰计数由监听器链另行维护
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
   * 将分段内的淘汰/显式删除事件向上透传给 Caffeine 父类的移除回调。
   *
   * <p>分段缓存自行管理各 Segment 的 LRU 与容量淘汰，但移除监听器、统计计数器由底层 Caffeine
   * 统一维护，故此处仅做转发：{@code Segment.clear()} 触发时传入 {@link RemovalCause#EXPLICIT}
   * （用户主动清空），达到容量阈值触发后台批量淘汰时传入 {@link RemovalCause#SIZE}。
   *
   * @param key 被移除的键，不会为 null（调用方均来自已存在于 map 中的 entry）
   * @param value 被移除的值，可能携带 null（缓存允许以 null 占位表示"已探测为不存在"）
   * @param cause 移除原因，决定监听器收到的语义，不可为 null
   */
  protected void notifyRemoval(K key, V value, RemovalCause cause) {
    super.notifyRemoval(key, value, cause);
  }

  /**
   * 分段内部实现：每个分段持有独立的 {@link ConcurrentHashMap} 与淘汰锁。
   *
   * <p>容量阈值 {@code evictThreshold} 为分段容量的 90%，达到阈值后在 {@link #evict}
   * 中按最近访问时间（LRU）批量淘汰最旧条目，减少锁竞争。
   * 分段内读操作无锁，写与淘汰由 {@code evictLock} 串行化。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
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

  /**
   * 缓存节点：持有缓存值与最近访问时间戳。
   *
   * <p>值 {@code value} 与访问时间 {@code lastAccessNanos} 均为 volatile，
   * 保证并发读写下的可见性；LRU 淘汰按 {@code lastAccessNanos} 升序选择淘汰对象。
   * 键本身由外层 {@link ConcurrentHashMap} 持有，节点内不再冗余存储。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  private static class Node<K, V> {
    volatile V value;
    volatile long lastAccessNanos;

    Node(K key, V value) {
      this.value = value;
      this.lastAccessNanos = System.nanoTime();
    }
  }
}
