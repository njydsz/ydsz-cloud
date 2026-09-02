package com.njydsz.common.json.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 字符串驻留工具（轻量级实现）
 *
 * <p>用于减少重复字符串的对象分配，提升序列化/反序列化性能。
 *
 * <p><b>设计思路：</b>
 *
 * <ul>
 *   <li>基于 {@link ConcurrentHashMap} 的无锁读路径（原实现的 synchronized 全局锁已移除，
 *       P1 修复：javadoc 曾声称"分段锁"实为方法级全局锁，高并发字段名驻留会完全串行化）
 *   <li>仅缓存短字符串（默认 ≤ 64 字符），避免大字符串占用内存
 *   <li>条目数达到上界（容量的 1.5 倍）时整表清空（P1 修复：原实现同样为整表清空，
 *       但 javadoc 虚标为"LRU 淘汰"；驻留是纯优化而非语义保证，清空安全——调用方
 *       拿到的字符串始终合法，仅失去引用合并收益）
 * </ul>
 *
 * <p><b>性能优势：</b>
 *
 * <ul>
 *   <li>避免重复字符串分配，减少 GC 压力
 *   <li>提升字符串比较性能（可直接用 == 比较引用）
 *   <li>对于高频重复字段名、枚举值等场景效果显著
 * </ul>
 *
 * <p><b>能力储备状态：</b>当前模块内部暂无调用方（{@code @Experimental}）。作为 L1 工具
 * 能力保留，启用前应补充并发基准测试（JMH）验证收益。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class StringInterner {

  /** 默认表容量（2 的幂次，便于位运算）。扩容至 4096 以适应生产环境字段名和短字符串数量。 */
  private static final int DEFAULT_CAPACITY = 4096;

  /** 最大字符串长度（超过此长度不缓存） */
  private static final int MAX_STRING_LENGTH = 64;

  /** 驻留表（无锁读，写路径为 CAS） */
  private final ConcurrentHashMap<String, String> table;

  /** 表容量（条目数上界 = 容量的 1.5 倍，超界整表清空） */
  private final int capacity;

  /** 缓存命中计数 */
  private final AtomicInteger hitCount = new AtomicInteger(0);

  /** 缓存未命中计数 */
  private final AtomicInteger missCount = new AtomicInteger(0);

  /** 创建默认大小的字符串驻留器 */
  public StringInterner() {
    this(DEFAULT_CAPACITY);
  }

  /**
   * 创建指定大小的字符串驻留器
   *
   * @param capacity 表容量（内部向上取整为 2 的幂次）
   */
  public StringInterner(int capacity) {
    // 确保容量是 2 的幂次
    int cap = 1;
    while (cap < capacity) {
      cap <<= 1;
    }
    this.capacity = cap;
    this.table = new ConcurrentHashMap<>(cap);
  }

  /**
   * 驻留字符串
   *
   * <p>如果字符串已存在于缓存中，则返回缓存的实例；否则将新字符串加入缓存并返回。
   *
   * @param str 待驻留的字符串
   * @return 驻留后的字符串实例（超长字符串原样返回，不做缓存）
   */
  public String intern(String str) {
    if (str == null) {
      return null;
    }

    // 长字符串不缓存
    if (str.length() > MAX_STRING_LENGTH) {
      return str;
    }

    String existing = table.putIfAbsent(str, str);
    if (existing != null) {
      hitCount.incrementAndGet();
      return existing;
    }

    missCount.incrementAndGet();

    // 有界控制：条目数超过容量 1.5 倍时整表清空（驻留为纯优化，清空不影响正确性）
    if (table.size() > capacity + (capacity >>> 1)) {
      table.clear();
    }
    return str;
  }

  /**
   * 获取缓存命中率
   *
   * @return 命中率（0.0 ~ 1.0）
   */
  public double getHitRate() {
    int hits = hitCount.get();
    int misses = missCount.get();
    int total = hits + misses;
    return total == 0 ? 0.0 : (double) hits / total;
  }

  /**
   * 获取缓存命中次数
   *
   * @return 命中次数
   */
  public int getHitCount() {
    return hitCount.get();
  }

  /**
   * 获取缓存未命中次数
   *
   * @return 未命中次数
   */
  public int getMissCount() {
    return missCount.get();
  }

  /** 重置统计信息 */
  public void resetStats() {
    hitCount.set(0);
    missCount.set(0);
  }
}
