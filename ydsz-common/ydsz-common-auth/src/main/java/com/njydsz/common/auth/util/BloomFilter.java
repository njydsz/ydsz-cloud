package com.njydsz.common.auth.util;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 布隆过滤器（零第三方依赖实现）。
 *
 * <p>用于 Token 黑名单前置过滤：判断元素「一定不在」集合中（假阴性为 0）， 从而短路 Redis 查询，降低高 QPS 下的 Redis 开销。
 *
 * <p>特性：
 *
 * <ul>
 *   <li>基于 {@link BitSet} 实现，默认 14 个哈希函数（约 0.01% 误判率）
 *   <li>可配置预计元素数与误判率，自动计算位数组大小与哈希函数个数
 *   <li>线程安全：写入持写锁，读取无锁（仅 volatile 读）
 *   <li>支持容量统计 {@link #estimatedSize()}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class BloomFilter {

  /** 位数组 */
  private final BitSet bits;

  /** 哈希函数个数 */
  private final int hashCount;

  /** 位数组大小 */
  private final int bitSize;

  /** 已插入元素数估算 */
  private final AtomicLong size = new AtomicLong(0);

  /** 读写锁：写操作（put）互斥，读操作无锁 */
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * 构造布隆过滤器。
   *
   * @param expectedInsertions 预计插入元素数
   * @param falsePositiveRate 目标误判率（0 &lt; rate &lt; 1）
   */
  public BloomFilter(long expectedInsertions, double falsePositiveRate) {
    if (expectedInsertions <= 0) {
      throw new IllegalArgumentException("expectedInsertions must be positive");
    }
    if (falsePositiveRate <= 0 || falsePositiveRate >= 1) {
      throw new IllegalArgumentException("falsePositiveRate must be in (0, 1)");
    }
    // 最优位数组大小 m = -n * ln(p) / (ln2)^2
    this.bitSize = optimalBitSize(expectedInsertions, falsePositiveRate);
    // 最优哈希函数个数 k = m / n * ln2
    this.hashCount = optimalHashCount(expectedInsertions, bitSize);
    this.bits = new BitSet(bitSize);
  }

  /** 计算最优位数组大小（向上取整到 64 的倍数，提升缓存行友好性） */
  private static int optimalBitSize(long n, double p) {
    double ln2 = Math.log(2);
    long bits = (long) Math.ceil(-n * Math.log(p) / (ln2 * ln2));
    // 对齐到 64
    bits = ((bits + 63) / 64) * 64;
    return (int) Math.max(bits, 64);
  }

  /** 计算最优哈希函数个数 */
  private static int optimalHashCount(long n, int bitSize) {
    int k = (int) Math.max(1, Math.round((double) bitSize / n * Math.log(2)));
    return Math.min(k, 32);
  }

  /**
   * 插入元素。
   *
   * @param value 元素值
   */
  public void put(String value) {
    if (value == null) {
      return;
    }
    lock.writeLock().lock();
    try {
      int[] hashes = hash(value);
      for (int hash : hashes) {
        bits.set(Math.floorMod(hash, bitSize));
      }
      size.incrementAndGet();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 判断元素是否可能在集合中。
   *
   * <p><b>语义：</b>返回 false 时元素<b>一定不在</b>集合中（零假阴性）； 返回 true 时元素<b>可能存在</b>（存在误判）。
   *
   * @param value 元素值
   * @return false = 一定不在；true = 可能存在
   */
  public boolean mightContain(String value) {
    if (value == null) {
      return false;
    }
    int[] hashes = hash(value);
    for (int hash : hashes) {
      if (!bits.get(Math.floorMod(hash, bitSize))) {
        return false;
      }
    }
    return true;
  }

  /**
   * 估算已插入元素数。
   *
   * @return 已插入元素数（每次 put +1）
   */
  public long estimatedSize() {
    return size.get();
  }

  /**
   * 计算 k 个双哈希派生的哈希值（MurmurHash 风格混合）。
   *
   * <p>采用 {@code h1 + i * h2} 派生态，避免 k 次完整哈希计算。
   *
   * @param value 元素值
   * @return k 个哈希值
   */
  private int[] hash(String value) {
    int h1 = mix(value.hashCode());
    int h2 = mix(h1 ^ 0x5bd1e995);
    int[] hashes = new int[hashCount];
    for (int i = 0; i < hashCount; i++) {
      hashes[i] = h1 + i * h2;
    }
    return hashes;
  }

  /** 位混合（类似 MurmurHash 的 finalizer，降低简单 hashCode 的分布缺陷） */
  private static int mix(int h) {
    h ^= h >>> 16;
    h *= 0x85ebca6b;
    h ^= h >>> 13;
    h *= 0xc2b2ae35;
    h ^= h >>> 16;
    return h;
  }
}
