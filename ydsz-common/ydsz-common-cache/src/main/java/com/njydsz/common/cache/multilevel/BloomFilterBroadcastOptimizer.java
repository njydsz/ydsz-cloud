package com.njydsz.common.cache.multilevel;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BloomFilter 广播优化器 — 减少无效的跨节点缓存失效广播
 *
 * <p>问题背景：
 *
 * <p>原实现中，每次 {@code put} 操作都会调用 {@code broadcaster.broadcastInvalidation()}，
 * 即使该 key 在本节点首次写入、其他节点 L1 中根本没有此 key（无效广播）。
 * 在写多读少场景下，广播流量会被放大 N 倍（N = 集群节点数）。
 *
 * <p>优化原理：
 *
 * <ol>
 *   <li>节点维护一个 BloomFilter，记录"可能被其他节点 L1 缓存的 key"
 *   <li>key 从 L2 回填到其他节点 L1 时（即其他节点执行 {@code getIfPresent} L1 miss → L2 hit），
 *       本节点将该 key 加入 BloomFilter
 *   <li>{@code put} 时先检查 BloomFilter：
 *     <ul>
 *       <li>key 不在 BloomFilter 中 → 其他节点 L1 肯定没有此 key → 跳过广播</li>
 *       <li>key 在 BloomFilter 中 → 可能有 → 正常广播</li>
 *     </ul>
 * </ol>
 *
 * <p>BloomFilter 特性：
 * <ul>
 *   <li>无 false negative：如果 key 不在集合中，则一定不在（不误判）
 *   <li>可控 false positive：如果 key 在集合中，可能有误判（但可通过参数控制）
 *   <li>内存高效：每个 key 约 10 bits（例如 100 万 key 仅需 ~1.2 MB）
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * CacheInvalidationBroadcaster baseBroadcaster = new RedisCacheInvalidationBroadcaster(...);
 * BloomFilterBroadcastOptimizer optimizer = new BloomFilterBroadcastOptimizer(
 *     baseBroadcaster, 1000000, 0.01);
 * // 使用 optimizer 替换原 broadcaster
 * MultiLevelCache<String, V> cache = new MultiLevelCache<>(
 *     l1, l2, "myCache", optimizer, rebuildLock);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class BloomFilterBroadcastOptimizer implements CacheInvalidationBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(BloomFilterBroadcastOptimizer.class);

  /** 底层实际广播器 */
  private final CacheInvalidationBroadcaster delegate;

  /** BloomFilter 位数组 */
  private final BitSet bitSet;

  /** BloomFilter 大小（bit 数） */
  private final int bitSetSize;

  /** 预期元素数量 */
  private final int expectedInsertions;

  /** 可接受的误判率 */
  private final double fpp;

  /** 使用的哈希函数数量 */
  private final int numHashFunctions;

  /** 已插入元素计数 */
  private final AtomicLong insertionCount = new AtomicLong(0);

  /** 注册的处理器 */
  private final java.util.List<InvalidationHandler> handlers = new java.util.concurrent.CopyOnWriteArrayList<>();

  /** 本地缓存注册表 */
  private final ConcurrentHashMap<String, com.njydsz.common.cache.api.Cache<?, ?>> localCaches = new ConcurrentHashMap<>();

  /** BloomFilter 读写锁（写锁用于重置，读锁用于查询） */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * 创建 BloomFilter 广播优化器
   *
   * @param delegate 底层实际广播器
   * @param expectedInsertions 预期缓存 key 数量
   * @param fpp 可接受的误判率（false positive probability），建议 0.01-0.001
   */
  public BloomFilterBroadcastOptimizer(
      CacheInvalidationBroadcaster delegate, int expectedInsertions, double fpp) {
    this.delegate = delegate;
    this.expectedInsertions = expectedInsertions;
    this.fpp = fpp;
    this.bitSetSize = optimalBitSize(expectedInsertions, fpp);
    this.numHashFunctions = optimalNumHashFunctions(expectedInsertions, bitSetSize);
    this.bitSet = new BitSet(bitSetSize);

    // 注册底层广播器的处理器
    delegate.registerHandler((cacheName, key, clearAll) -> {
      // 重置 BloomFilter（全量清除时）
      if (clearAll) {
        reset();
      }
      // 通知注册的处理器
      for (InvalidationHandler handler : handlers) {
        try {
          handler.onInvalidation(cacheName, key, clearAll);
        } catch (Exception e) {
          log.warn("处理器执行异常", e);
        }
      }
    });

    log.info("BloomFilterBroadcastOptimizer 已创建: expectedInsertions={}, fpp={}, bitSetSize={}, hashFunctions={}",
        expectedInsertions, fpp, bitSetSize, numHashFunctions);
  }

  /**
   * 注册本地缓存实例
   *
   * @param cacheName 缓存名称
   * @param cache 本地 L1 缓存实例
   */
  public void registerLocalCache(String cacheName, com.njydsz.common.cache.api.Cache<?, ?> cache) {
    localCaches.put(cacheName, cache);
  }

  /**
   * 标记 key 可能被其他节点缓存（从 L2 回填 L1 时调用）
   *
   * <p>当本节点从 L2 回填到 L1 时，说明此 key 也在其他节点的 L2 缓存中，
   * 其他节点也可能将其缓存到 L1。因此需要加入 BloomFilter。
   *
   * @param key 缓存 key
   */
  public void markKeyCached(Object key) {
    if (key == null) {
      return;
    }
    byte[] bytes = key.toString().getBytes(StandardCharsets.UTF_8);
    lock.readLock().lock();
    try {
      for (int hash : getHashValues(bytes)) {
        bitSet.set(hash);
      }
      insertionCount.incrementAndGet();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 批量标记 key
   *
   * @param keys 缓存 key 集合
   */
  public void markKeysCached(Collection<?> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    lock.readLock().lock();
    try {
      for (Object key : keys) {
        if (key != null) {
          byte[] bytes = key.toString().getBytes(StandardCharsets.UTF_8);
          for (int hash : getHashValues(bytes)) {
            bitSet.set(hash);
          }
        }
      }
      insertionCount.addAndGet(keys.size());
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public void broadcastInvalidation(String cacheName, Object key) {
    if (key == null) {
      return;
    }
    // BloomFilter 检查：key 不可能在其它节点 L1 时跳过广播（减少无效广播）
    if (!mightBeCachedOnOtherNodes(key)) {
      log.debug("BloomFilter 判定 key 不在其他节点 L1 中，跳过广播: key={}", key);
      return;
    }
    delegate.broadcastInvalidation(cacheName, key);
  }

  @Override
  public void broadcastInvalidationAll(String cacheName, Collection<Object> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    // 批量过滤：只广播可能在其他节点缓存的 key
    for (Object key : keys) {
      if (key != null && mightBeCachedOnOtherNodes(key)) {
        delegate.broadcastInvalidation(cacheName, key);
      }
    }
  }

  @Override
  public void broadcastClearAll(String cacheName) {
    // 全量清除一定广播（清空所有节点的 L1）
    delegate.broadcastClearAll(cacheName);
  }

  @Override
  public void registerHandler(InvalidationHandler handler) {
    if (handler != null) {
      handlers.add(handler);
    }
  }

  /**
   * 判断 key 是否可能在其他节点 L1 中缓存
   *
   * @param key 缓存 key
   * @return true 表示可能在（需要广播）；false 表示一定不在（跳过广播）
   */
  private boolean mightBeCachedOnOtherNodes(Object key) {
    if (key == null) {
      return false;
    }
    byte[] bytes = key.toString().getBytes(StandardCharsets.UTF_8);
    lock.readLock().lock();
    try {
      for (int hash : getHashValues(bytes)) {
        if (!bitSet.get(hash)) {
          return false; // 任何一个位为 false，则 key 一定不在集合中
        }
      }
      return true; // 所有位为 true，key 可能在集合中
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 计算多个哈希值（双哈希模拟 k 个哈希函数）
   *
   * <p>使用 Kirsch-Mitzenmacher 优化：通过两个基础哈希函数生成 k 个哈希值
   * h_i(x) = h1(x) + i * h2(x)
   */
  private int[] getHashValues(byte[] bytes) {
    int[] hashes = new int[numHashFunctions];
    int hash1 = murmurHash3(bytes, 0);
    int hash2 = murmurHash3(bytes, hash1);
    for (int i = 0; i < numHashFunctions; i++) {
      hashes[i] = Math.abs((hash1 + i * hash2) % bitSetSize);
    }
    return hashes;
  }

  /**
   * MurmurHash3 32-bit 实现
   */
  private int murmurHash3(byte[] data, int seed) {
    int h1 = seed;
    final int c1 = 0xcc9e2d51;
    final int c2 = 0x1b873593;
    int len = data.length;
    int roundedEnd = len & 0xfffffffc;

    for (int i = 0; i < roundedEnd; i += 4) {
      int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
          | ((data[i + 2] & 0xff) << 16) | ((data[i + 3] & 0xff) << 24);
      k1 *= c1;
      k1 = Integer.rotateLeft(k1, 15);
      k1 *= c2;
      h1 ^= k1;
      h1 = Integer.rotateLeft(h1, 13);
      h1 = h1 * 5 + 0xe6546b64;
    }

    int k1 = 0;
    switch (len & 0x03) {
      case 3:
        k1 = (data[roundedEnd + 2] & 0xff) << 16;
        // fall through
      case 2:
        k1 |= (data[roundedEnd + 1] & 0xff) << 8;
        // fall through
      case 1:
        k1 |= (data[roundedEnd] & 0xff);
        k1 *= c1;
        k1 = Integer.rotateLeft(k1, 15);
        k1 *= c2;
        h1 ^= k1;
      default:
        break;
    }

    h1 ^= len;
    // fmix32
    h1 ^= h1 >>> 16;
    h1 *= 0x85ebca6b;
    h1 ^= h1 >>> 13;
    h1 *= 0xc2b2ae35;
    h1 ^= h1 >>> 16;

    return h1;
  }

  /** 重置 BloomFilter */
  public void reset() {
    lock.writeLock().lock();
    try {
      bitSet.clear();
      insertionCount.set(0);
      log.info("BloomFilter 已重置");
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 获取当前插入元素计数
   *
   * @return 插入元素数量
   */
  public long getInsertionCount() {
    return insertionCount.get();
  }

  /**
   * 获取 BloomFilter 参数信息
   *
   * @return 参数描述字符串
   */
  public String getBloomFilterInfo() {
    return String.format(
        "BloomFilter{bits=%d, hashFunctions=%d, expectedInsertions=%d, fpp=%.4f, insertionCount=%d}",
        bitSetSize, numHashFunctions, expectedInsertions, fpp, insertionCount.get());
  }

  /**
   * 计算最优 bit 数组大小
   *
   * <p>公式：m = -n * ln(p) / (ln(2)^2)
   */
  static int optimalBitSize(int n, double p) {
    if (p == 0) {
      p = Double.MIN_VALUE;
    }
    return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
  }

  /**
   * 计算最优哈希函数数量
   *
   * <p>公式：k = (m/n) * ln(2)
   */
  static int optimalNumHashFunctions(int n, int m) {
    return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
  }
}
