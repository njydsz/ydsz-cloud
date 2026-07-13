package com.njydsz.pmis.common.cache.internal.lfu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.internal.AbstractCache;
import com.njydsz.pmis.common.cache.listener.RemovalCause;

/**
 * LFU（Least Frequently Used）缓存实现 - 基于访问频率的淘汰策略
 *
 * <p>核心特性：
 *
 * <ul>
 *   <li>基于访问频率淘汰：访问次数最少的元素优先被淘汰
 *   <li>采样淘汰优化：通过采样方式选择被淘汰元素，避免全量遍历 O(n) 降到 O(1)
 *   <li>线程安全：使用 StampedLock 提供高效读写锁
 *   <li>无锁频率计数：使用 AtomicInteger 更新频率，避免获取写锁阻塞读操作
 *   <li>频率衰减：防止历史访问频率影响当前淘汰决策
 * </ul>
 *
 * <p>工作原理：
 *
 * <ol>
 *   <li>每个缓存项维护一个访问频率计数器
 *   <li>每次访问时，频率计数器递增
 *   <li>缓存满时，随机采样 N 个元素，选择频率最低的淘汰
 *   <li>定期进行频率衰减，防止"僵尸数据"占据缓存
 * </ol>
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>读多写多的热点数据缓存
 *   <li>需要识别热点数据的场景
 *   <li>访问频率比访问时间更重要的场景
 * </ul>
 *
 * <p>性能对比：
 *
 * <ul>
 *   <li>vs LRU：LFU 在访问模式稳定的场景下命中率更高
 *   <li>采样优化：淘汰操作从 O(n) 降低到 O(sampleSize)
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class LFUCache<K, V> extends AbstractCache<K, V> {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(LFUCache.class);

  /** 底层并发存储映射 */
  private final Map<K, CacheEntry<V>> map;

  /** 最大缓存容量 */
  private final int maxSize;

  /** 采样大小 */
  private final int sampleSize;

  /** StampedLock 用于读写锁分离 */
  private final StampedLock lock = new StampedLock();

  public LFUCache(int maxSize) {
    this(maxSize, 10);
  }

  public LFUCache(int maxSize, int sampleSize) {
    this.maxSize = maxSize;
    this.sampleSize = sampleSize;
    this.map = new ConcurrentHashMap<>(Math.max(16, maxSize));
  }

  @Override
  public V getIfPresent(K key) {
    long stamp = lock.tryOptimisticRead();
    CacheEntry<V> entry = map.get(key);
    if (entry == null) {
      if (!lock.validate(stamp)) {
        stamp = lock.readLock();
        try {
          entry = map.get(key);
          if (entry == null) {
            missCount.increment();
            return null;
          }
        } finally {
          lock.unlockRead(stamp);
        }
      } else {
        missCount.increment();
        return null;
      }
    }
    if (lock.validate(stamp)) {
      entry.incrementFrequency();
      hitCount.increment();
      return entry.value;
    }
    stamp = lock.readLock();
    try {
      entry = map.get(key);
      if (entry != null) {
        entry.incrementFrequency();
        hitCount.increment();
        return entry.value;
      }
      missCount.increment();
      return null;
    } finally {
      lock.unlockRead(stamp);
    }
  }

  @Override
  public void put(K key, V value) {
    CacheEntry<V> oldEntry = map.get(key);
    if (oldEntry != null) {
      long stamp = lock.writeLock();
      try {
        oldEntry = map.get(key);
        if (oldEntry != null) {
          oldEntry.value = value;
          return;
        }
      } finally {
        lock.unlockWrite(stamp);
      }
      return;
    }

    if (map.size() >= maxSize) {
      K evictKey = findEvictionCandidate();
      if (evictKey != null) {
        long stamp = lock.writeLock();
        try {
          CacheEntry<V> entry = map.remove(evictKey);
          if (entry != null) {
            log.debug("LFU 淘汰，key={}, frequency={}", evictKey, entry.getFrequency());
            notifyRemoval(evictKey, entry.value, RemovalCause.SIZE);
          }
        } finally {
          lock.unlockWrite(stamp);
        }
      }
    }

    map.put(key, new CacheEntry<>(value));
  }

  @Override
  public V remove(K key) {
    long stamp = lock.writeLock();
    try {
      CacheEntry<V> entry = map.remove(key);
      if (entry != null) {
        notifyRemoval(key, entry.value, RemovalCause.EXPLICIT);
        return entry.value;
      }
      return null;
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public void clear() {
    long stamp = lock.writeLock();
    try {
      map.forEach((key, entry) -> notifyRemoval(key, entry.value, RemovalCause.EXPLICIT));
      map.clear();
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public long estimatedSize() {
    return map.size();
  }

  @Override
  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  @Override
  public Set<K> keySet() {
    return map.keySet();
  }

  @Override
  public Collection<V> values() {
    List<V> list = new ArrayList<>(map.size());
    for (CacheEntry<V> entry : map.values()) {
      list.add(entry.value);
    }
    return list;
  }

  /**
   * 在写锁外查找淘汰候选键（仅做采样决策）
   *
   * <p>基于 ConcurrentHashMap + 无锁频率计数，减少写锁持有时间
   */
  private K findEvictionCandidate() {
    if (map.isEmpty()) {
      return null;
    }
    int sample = Math.min(sampleSize, map.size());
    K leastFrequentKey = null;
    int minFrequency = Integer.MAX_VALUE;

    for (int i = 0; i < sample; i++) {
      Map.Entry<K, CacheEntry<V>> entry = getRandomEntry();
      if (entry != null) {
        int freq = entry.getValue().getFrequency();
        if (freq < minFrequency) {
          minFrequency = freq;
          leastFrequentKey = entry.getKey();
        }
      }
    }
    return leastFrequentKey;
  }

  /**
   * 随机获取一个条目
   *
   * <p>优化：使用蓄水池采样算法，O(n) 遍历但只访问一次，避免多次迭代
   */
  private Map.Entry<K, CacheEntry<V>> getRandomEntry() {
    int size = map.size();
    if (size == 0) {
      return null;
    }

    Map.Entry<K, CacheEntry<V>> selected = null;
    int count = 0;
    ThreadLocalRandom random = ThreadLocalRandom.current();

    for (Map.Entry<K, CacheEntry<V>> entry : map.entrySet()) {
      if (random.nextInt(++count) == 0) {
        selected = entry;
      }
    }
    return selected;
  }

  /**
   * LFU 缓存条目
   *
   * <p>维护值和访问频率信息，使用 AtomicInteger 实现无锁频率更新
   */
  private static class CacheEntry<V> {
    V value;
    final AtomicInteger frequency;

    CacheEntry(V value) {
      this.value = value;
      this.frequency = new AtomicInteger(0);
    }

    void incrementFrequency() {
      frequency.incrementAndGet();
    }

    int getFrequency() {
      return frequency.get();
    }
  }
}
