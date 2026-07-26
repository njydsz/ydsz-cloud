package com.njydsz.common.cache.stats;

/**
 * 缓存统计信息类
 *
 * <p>提供缓存运行时的各项统计指标，包括命中率、加载次数、加载耗时等， 用于监控缓存性能和调优。
 *
 * <p>主要指标：
 *
 * <ul>
 *   <li>hitCount：缓存命中次数
 *   <li>missCount：缓存未命中次数
 *   <li>loadCount：加载器调用总次数
 *   <li>loadSuccessCount：加载成功次数
 *   <li>loadExceptionCount：加载异常次数
 *   <li>totalLoadTimeNanos：总加载时间（纳秒）
 * </ul>
 *
 * <p>计算指标：
 *
 * <ul>
 *   <li>hitRate：命中率 = 命中次数 / (命中次数 + 未命中次数)
 *   <li>averageLoadPenalty：平均加载耗时 = 总加载时间 / 加载成功次数
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class CacheStats {

  /** 缓存命中次数 */
  private final long hitCount;

  /** 缓存未命中次数 */
  private final long missCount;

  /** 淘汰次数 */
  private final long evictionCount;

  /** 加载器调用总次数 */
  private final long loadCount;

  /** 加载成功次数 */
  private final long loadSuccessCount;

  /** 加载异常次数 */
  private final long loadExceptionCount;

  /** 总加载时间（纳秒） */
  private final long totalLoadTimeNanos;

  /** 空统计快照（零值） */
  public static final CacheStats EMPTY = new CacheStats(0, 0, 0, 0, 0, 0, 0);

  /**
   * 创建基础统计信息
   *
   * @param hitCount 命中次数
   * @param missCount 未命中次数
   */
  public CacheStats(long hitCount, long missCount) {
    this(hitCount, missCount, 0, 0, 0, 0, 0);
  }

  /**
   * 创建 CacheStats Builder
   *
   * @return Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  public CacheStats(
      long hitCount,
      long missCount,
      long evictionCount,
      long loadCount,
      long loadSuccessCount,
      long loadExceptionCount,
      long totalLoadTimeNanos) {
    this.hitCount = hitCount;
    this.missCount = missCount;
    this.evictionCount = evictionCount;
    this.loadCount = loadCount;
    this.loadSuccessCount = loadSuccessCount;
    this.loadExceptionCount = loadExceptionCount;
    this.totalLoadTimeNanos = totalLoadTimeNanos;
  }

  public CacheStats(
      long hitCount,
      long missCount,
      long loadCount,
      long loadSuccessCount,
      long loadExceptionCount,
      long totalLoadTimeNanos) {
    this(
        hitCount,
        missCount,
        0,
        loadCount,
        loadSuccessCount,
        loadExceptionCount,
        totalLoadTimeNanos);
  }

  /**
   * 获取缓存命中次数
   *
   * @return 命中次数
   */
  public long getHitCount() {
    return hitCount;
  }

  /**
   * 获取缓存未命中次数
   *
   * @return 未命中次数
   */
  public long getMissCount() {
    return missCount;
  }

  /**
   * 获取淘汰次数
   *
   * @return 淘汰次数
   */
  public long getEvictionCount() {
    return evictionCount;
  }

  /**
   * 获取加载器调用总次数
   *
   * @return 加载总次数
   */
  public long getLoadCount() {
    return loadCount;
  }

  /**
   * 获取加载成功次数
   *
   * @return 加载成功次数
   */
  public long getLoadSuccessCount() {
    return loadSuccessCount;
  }

  /**
   * 获取加载异常次数
   *
   * @return 加载异常次数
   */
  public long getLoadExceptionCount() {
    return loadExceptionCount;
  }

  /**
   * 获取总加载时间（纳秒）
   *
   * @return 总加载时间（纳秒）
   */
  public long getTotalLoadTimeNanos() {
    return totalLoadTimeNanos;
  }

  /**
   * 计算缓存命中率
   *
   * <p>命中率 = 命中次数 / (命中次数 + 未命中次数) 当总访问次数为 0 时，返回 0.0
   *
   * @return 命中率（0.0 ~ 1.0）
   */
  public double getHitRate() {
    long total = hitCount + missCount;
    return total == 0 ? 0.0 : (double) hitCount / total;
  }

  /**
   * 计算平均加载耗时
   *
   * <p>平均加载耗时 = 总加载时间 / 加载成功次数 当加载成功次数为 0 时，返回 0.0
   *
   * @return 平均加载耗时（纳秒）
   */
  public double getAverageLoadPenalty() {
    return loadSuccessCount == 0 ? 0.0 : (double) totalLoadTimeNanos / loadSuccessCount;
  }

  /**
   * 计算加载成功率
   *
   * <p>成功率 = 加载成功次数 / 加载总次数 当加载总次数为 0 时，返回 0.0
   *
   * @return 加载成功率（0.0 ~ 1.0）
   */
  public double getLoadSuccessRate() {
    return loadCount == 0 ? 0.0 : (double) loadSuccessCount / loadCount;
  }

  /**
   * 计算未命中率
   *
   * <p>未命中率 = 未命中次数 / (命中次数 + 未命中次数) 当总访问次数为 0 时，返回 0.0
   *
   * @return 未命中率（0.0 ~ 1.0）
   */
  public double getMissRate() {
    return 1.0 - getHitRate();
  }

  /**
   * 获取总访问次数
   *
   * @return 总访问次数 = 命中次数 + 未命中次数
   */
  public long getTotalAccessCount() {
    return hitCount + missCount;
  }

  /**
   * 获取平均每次访问的加载耗时（纳秒）
   *
   * @return 平均每次访问的加载耗时
   */
  public double getAverageLoadPenaltyPerAccess() {
    long total = getTotalAccessCount();
    return total == 0 ? 0.0 : (double) totalLoadTimeNanos / total;
  }

  /**
   * 合并两个统计快照
   *
   * @param other 另一个统计快照
   * @return 合并后的统计快照
   */
  public CacheStats plus(CacheStats other) {
    return new CacheStats(
        hitCount + other.hitCount,
        missCount + other.missCount,
        evictionCount + other.evictionCount,
        loadCount + other.loadCount,
        loadSuccessCount + other.loadSuccessCount,
        loadExceptionCount + other.loadExceptionCount,
        totalLoadTimeNanos + other.totalLoadTimeNanos);
  }

  /**
   * 计算两个统计快照的差值
   *
   * @param other 另一个统计快照
   * @return 差值统计快照
   */
  public CacheStats minus(CacheStats other) {
    return new CacheStats(
        Math.max(0, hitCount - other.hitCount),
        Math.max(0, missCount - other.missCount),
        Math.max(0, evictionCount - other.evictionCount),
        Math.max(0, loadCount - other.loadCount),
        Math.max(0, loadSuccessCount - other.loadSuccessCount),
        Math.max(0, loadExceptionCount - other.loadExceptionCount),
        Math.max(0, totalLoadTimeNanos - other.totalLoadTimeNanos));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CacheStats)) return false;
    CacheStats that = (CacheStats) o;
    return hitCount == that.hitCount
        && missCount == that.missCount
        && evictionCount == that.evictionCount
        && loadCount == that.loadCount
        && loadSuccessCount == that.loadSuccessCount
        && loadExceptionCount == that.loadExceptionCount
        && totalLoadTimeNanos == that.totalLoadTimeNanos;
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(hitCount);
    result = 31 * result + Long.hashCode(missCount);
    result = 31 * result + Long.hashCode(evictionCount);
    result = 31 * result + Long.hashCode(loadCount);
    result = 31 * result + Long.hashCode(loadSuccessCount);
    result = 31 * result + Long.hashCode(loadExceptionCount);
    result = 31 * result + Long.hashCode(totalLoadTimeNanos);
    return result;
  }

  @Override
  public String toString() {
    return String.format(
        "CacheStats{hitCount=%d, missCount=%d, hitRate=%.2f%%, loadCount=%d, "
            + "loadSuccessCount=%d, loadExceptionCount=%d, avgLoadPenalty=%.2fms}",
        hitCount,
        missCount,
        getHitRate() * 100,
        loadCount,
        loadSuccessCount,
        loadExceptionCount,
        getAverageLoadPenalty() / 1_000_000.0);
  }

  /**
   * CacheStats 构建器 — 链式构建统计快照
   *
   * <p>使用示例：
   *
   * <pre>{@code
   * CacheStats stats = CacheStats.builder()
   *     .hitCount(100)
   *     .missCount(20)
   *     .evictionCount(5)
   *     .loadSuccessCount(15)
   *     .totalLoadTimeNanos(3_000_000)
   *     .build();
   * }</pre>
   *
   * @since 1.0.0
   */
  public static final class Builder {
    private long hitCount;
    private long missCount;
    private long evictionCount;
    private long loadCount;
    private long loadSuccessCount;
    private long loadExceptionCount;
    private long totalLoadTimeNanos;

    private Builder() {}

    /**
     * 设置命中次数
     *
     * @param hitCount 命中次数
     * @return this
     */
    public Builder hitCount(long hitCount) {
      this.hitCount = hitCount;
      return this;
    }

    /**
     * 设置未命中次数
     *
     * @param missCount 未命中次数
     * @return this
     */
    public Builder missCount(long missCount) {
      this.missCount = missCount;
      return this;
    }

    /**
     * 设置淘汰次数
     *
     * @param evictionCount 淘汰次数
     * @return this
     */
    public Builder evictionCount(long evictionCount) {
      this.evictionCount = evictionCount;
      return this;
    }

    /**
     * 设置加载总次数
     *
     * @param loadCount 加载总次数
     * @return this
     */
    public Builder loadCount(long loadCount) {
      this.loadCount = loadCount;
      return this;
    }

    /**
     * 设置加载成功次数
     *
     * @param loadSuccessCount 加载成功次数
     * @return this
     */
    public Builder loadSuccessCount(long loadSuccessCount) {
      this.loadSuccessCount = loadSuccessCount;
      return this;
    }

    /**
     * 设置加载异常次数
     *
     * @param loadExceptionCount 加载异常次数
     * @return this
     */
    public Builder loadExceptionCount(long loadExceptionCount) {
      this.loadExceptionCount = loadExceptionCount;
      return this;
    }

    /**
     * 设置总加载时间
     *
     * @param totalLoadTimeNanos 总加载时间（纳秒）
     * @return this
     */
    public Builder totalLoadTimeNanos(long totalLoadTimeNanos) {
      this.totalLoadTimeNanos = totalLoadTimeNanos;
      return this;
    }

    /**
     * 构建统计快照
     *
     * @return 不可变统计快照
     */
    public CacheStats build() {
      return new CacheStats(
          hitCount,
          missCount,
          evictionCount,
          loadCount,
          loadSuccessCount,
          loadExceptionCount,
          totalLoadTimeNanos);
    }
  }
}
