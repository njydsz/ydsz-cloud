package com.njydsz.common.safe.resilience;

/**
 * 滑动窗口统计器（包内实现）。
 *
 * <p>支持两种窗口模式：
 *
 * <ul>
 *   <li>COUNT_BASED：固定容量环形缓冲，仅统计最近 N 次调用
 *   <li>TIME_BASED：按秒分桶，统计最近 N 秒的调用
 * </ul>
 *
 * <p>实现说明：record 与 snapshot 在同一把锁内完成，保证统计一致性；锁临界区仅为几次
 * 整数累加，相对毫秒级的网络调用开销可忽略（该取舍已在 ADR-0004 记录）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
final class SlidingWindowMetrics {

  private final boolean timeBased;
  private final int size;
  private final Object lock = new Object();
  private final Bucket[] buckets;
  private long head;

  /**
   * 构造滑动窗口。
   *
   * @param type 窗口类型
   * @param size COUNT_BASED 为调用次数；TIME_BASED 为秒数
   */
  SlidingWindowMetrics(CircuitBreakerConfig.SlidingWindowType type, int size) {
    this.timeBased = type == CircuitBreakerConfig.SlidingWindowType.TIME_BASED;
    this.size = size;
    this.buckets = new Bucket[size];
    for (int i = 0; i < size; i++) {
      buckets[i] = new Bucket();
    }
  }

  /**
   * 记录一次调用结果。
   *
   * @param durationMs 调用耗时（毫秒）
   * @param failure 是否失败
   * @param slow 是否慢调用
   */
  void record(long durationMs, boolean failure, boolean slow) {
    synchronized (lock) {
      if (timeBased) {
        long epochSecond = System.currentTimeMillis() / 1000L;
        int index = (int) Math.floorMod(epochSecond, size);
        Bucket bucket = buckets[index];
        if (bucket.epochSecond != epochSecond) {
          bucket.reset(epochSecond);
        }
        bucket.record(durationMs, failure, slow);
        return;
      }
      Bucket bucket = buckets[(int) (head % size)];
      bucket.reset(head / size + 1);
      bucket.record(durationMs, failure, slow);
      head++;
    }
  }

  /**
   * 生成窗口快照。
   *
   * @return 当前窗口统计快照
   */
  Snapshot snapshot() {
    synchronized (lock) {
      Snapshot snapshot = new Snapshot();
      if (timeBased) {
        long epochSecond = System.currentTimeMillis() / 1000L;
        long windowStart = epochSecond - size + 1;
        for (Bucket bucket : buckets) {
          if (bucket.epochSecond >= windowStart && bucket.epochSecond <= epochSecond) {
            snapshot.add(bucket);
          }
        }
        return snapshot;
      }
      for (Bucket bucket : buckets) {
        snapshot.add(bucket);
      }
      return snapshot;
    }
  }

  /** 清空窗口统计。 */
  void reset() {
    synchronized (lock) {
      for (Bucket bucket : buckets) {
        bucket.reset(0);
      }
      head = 0;
    }
  }

  /** 单桶统计（COUNT_BASED 为一个调用槽位，TIME_BASED 为一秒）。 */
  private static final class Bucket {

    private long epochSecond;
    private long totalDurationMs;
    private int total;
    private int failure;
    private int slow;
    private int slowFailure;

    private void reset(long epochSecond) {
      this.epochSecond = epochSecond;
      this.totalDurationMs = 0L;
      this.total = 0;
      this.failure = 0;
      this.slow = 0;
      this.slowFailure = 0;
    }

    private void record(long durationMs, boolean failure, boolean slow) {
      this.totalDurationMs += durationMs;
      this.total++;
      if (failure) {
        this.failure++;
      }
      if (slow) {
        this.slow++;
        if (failure) {
          this.slowFailure++;
        }
      }
    }
  }

  /** 窗口统计快照（不可变视图）。 */
  static final class Snapshot {

    private long totalDurationMs;
    private int total;
    private int failure;
    private int slow;
    private int slowFailure;

    private void add(Bucket bucket) {
      this.totalDurationMs += bucket.totalDurationMs;
      this.total += bucket.total;
      this.failure += bucket.failure;
      this.slow += bucket.slow;
      this.slowFailure += bucket.slowFailure;
    }

    /**
     * 获取窗口内总调用数。
     *
     * @return 总调用数
     */
    int getTotal() {
      return total;
    }

    /**
     * 获取窗口内失败调用数。
     *
     * @return 失败调用数
     */
    int getFailure() {
      return failure;
    }

    /**
     * 获取窗口内慢调用数（含慢成功与慢失败）。
     *
     * @return 慢调用数
     */
    int getSlow() {
      return slow;
    }

    /**
     * 获取窗口内慢失败调用数。
     *
     * @return 慢失败调用数
     */
    int getSlowFailure() {
      return slowFailure;
    }

    /**
     * 获取窗口内慢成功调用数。
     *
     * @return 慢成功调用数
     */
    int getSlowSuccess() {
      return slow - slowFailure;
    }

    /**
     * 获取窗口内成功调用数（总调用数减失败调用数）。
     *
     * @return 成功调用数
     */
    int getSuccess() {
      return total - failure;
    }

    /**
     * 获取窗口内总耗时（毫秒）。
     *
     * @return 总耗时
     */
    long getTotalDurationMs() {
      return totalDurationMs;
    }

    /**
     * 获取失败率（百分比；无调用时返回 -1）。
     *
     * @return 失败率
     */
    float getFailureRate() {
      if (total == 0) {
        return -1.0f;
      }
      return failure * 100.0f / total;
    }

    /**
     * 获取慢调用率（百分比；无调用时返回 -1）。
     *
     * @return 慢调用率
     */
    float getSlowCallRate() {
      if (total == 0) {
        return -1.0f;
      }
      return slow * 100.0f / total;
    }

    /**
     * 获取平均调用耗时（毫秒；无调用时返回 0）。
     *
     * @return 平均耗时
     */
    long getAverageDurationMs() {
      if (total == 0) {
        return 0L;
      }
      return totalDurationMs / total;
    }
  }
}
