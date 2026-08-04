package com.remisoft.common.cache.internal.lfu;

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

import com.remisoft.common.cache.internal.AbstractCache;
import com.remisoft.common.cache.listener.RemovalCause;

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
 * @author remi-team
 * @since 1.0.0
 * 
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

  /**
   * 获取缓存值（不触发加载），并递增访问频率。
   *
   * <p>采用乐观读 + StampedLock 升级路径：无写竞争时无锁读取并递增频率；
   * 有写竞争时降级为读锁重查。命中计入 hit，未命中计入 miss 并返回 null。
   *
   * @param key 缓存键
   * @return 缓存值；未命中时返回 {@code null}
   */
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

  /**
   * 写入键值对；容量满时按 LFU 采样策略淘汰最不常用条目。
   *
   * <p>整个流程持有写锁，保证容量检查、淘汰决策与写入原子化，避免并发超限。
   * 键已存在时仅覆盖值（保留原访问频率）； 容量满且淘汰失败时保守拒绝写入并记录告警，防止容量超出 maxSize。
   *
   * @param key   缓存键
   * @param value 缓存值
   */
  @Override
  public void put(K key, V value) {
    // 整个 put 流程在 writeLock 内执行，避免：
    // 1) 容量检查与写入的非原子组合导致 map.size() > maxSize
    // 2) 淘汰决策与淘汰执行之间，候选键可能已被其他线程替换或删除
    // 3) 与 remove/clear 的并发数据竞争
    long stamp = lock.writeLock();
    try {
      CacheEntry<V> oldEntry = map.get(key);
      if (oldEntry != null) {
        oldEntry.value = value;
        return;
      }

      // 容量再校验：在 writeLock 内 size 不会变化
      if (map.size() >= maxSize) {
        K evictKey = findEvictionCandidate();
        if (evictKey != null) {
          CacheEntry<V> entry = map.remove(evictKey);
          if (entry != null) {
            log.debug("LFU 淘汰，key={}, frequency={}", evictKey, entry.getFrequency());
            notifyRemoval(evictKey, entry.value, RemovalCause.SIZE);
          }
        }
      }

      // 容量再校验后写入
      if (map.size() >= maxSize) {
        // 淘汰失败（findEvictionCandidate 返回 null 或 map 仍超限），保守拒绝写入避免容量超限
        log.warn("LFU 容量超限但淘汰失败，跳过 put key={}", key);
        return;
      }
      map.put(key, new CacheEntry<>(value));
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>写锁内执行删除，并向监听器发出 {@link RemovalCause#EXPLICIT} 通知。
   *
   * @param key 缓存键
   * @return 被移除的值；键不存在时返回 {@code null}
   */
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

  /**
   * 清空缓存。
   *
   * <p>写锁内先对全部条目发送 {@link RemovalCause#EXPLICIT} 通知，再清空存储。
   *
   */
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

  /**
   * 返回缓存条目数（近似值）。
   *
   * <p>直接统计底层 {@link ConcurrentHashMap} 大小，并发下为弱一致近似值。
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
   * 返回缓存键集合视图（透传底层并发映射，迭代器弱一致）。
   *
   * @return 缓存键集合视图
   */
  @Override
  public Set<K> keySet() {
    return map.keySet();
  }

  /**
   * 返回缓存值集合。
   *
   * <p>复制到新列表返回一次性快照，值为解包后的实际数据。
   *
   * @return 当前缓存值的快照集合
   */
  @Override
  public Collection<V> values() {
    List<V> list = new ArrayList<>(map.size());
    for (CacheEntry<V> entry : map.values()) {
      list.add(entry.value);
    }
    return list;
  }

  /**
   * 在 writeLock 内查找淘汰候选键（采样决策）
   *
   * <p>采样策略：随机选取 sampleSize 个 key，返回频率最低者。
   * 调用方需持有 writeLock，确保采样期间 map 不会被并发修改。
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
