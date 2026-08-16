package com.njydsz.common.lock.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 分布式锁指标收集器
 *
 * <p>提供两层指标采集：
 *
 * <ul>
 *   <li>内部计数器：基于 LongAdder/AtomicLong/AtomicInteger，始终可用，零依赖
 *   <li>Micrometer 指标：条件化注册，当 classpath 存在 MeterRegistry 时自动启用
 * </ul>
 *
 * <p><b>内部计数器指标：</b>
 *
 * <ul>
 *   <li>acquireSuccessCount - 获取锁成功次数
 *   <li>acquireFailCount - 获取锁失败次数
 *   <li>releaseCount - 释放锁次数
 *   <li>averageWaitTimeMillis - 平均等待时间
 *   <li>averageHoldTimeMillis - 平均持有时间
 *   <li>competitionCount - 锁竞争次数
 *   <li>activeLocks - 当前活跃锁数量
 *   <li>lockTimeoutCount - 锁超时次数
 *   <li>watchdogRenewCount - 续期次数
 * </ul>
 *
 * <p><b>Micrometer/Prometheus 指标：</b>
 *
 * <ul>
 *   <li>lock.acquire.total - 获取锁成功总数（Counter，标签: lock_type）
 *   <li>lock.acquire.failed.total - 获取锁失败总数（Counter，标签: lock_type）
 *   <li>lock.release.total - 释放锁总数（Counter，标签: lock_type）
 *   <li>lock.hold.duration - 锁持有耗时（Timer，标签: lock_type）
 *   <li>lock.competition.count - 锁竞争次数（Counter，标签: lock_type, lock_key）
 *   <li>lock.wait.duration - 锁等待时间分布（Timer，标签: lock_type）
 *   <li>lock.active.locks - 当前活跃锁数量（Gauge）
 *   <li>lock.timeout.count - 锁超时次数（Counter，标签: lock_type）
 *   <li>lock.watchdog.renew.count - 续期次数（Counter，标签: lock_type）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LockMetrics {

  /** 获取锁成功次数 */
  private final LongAdder acquireSuccessCount = new LongAdder();

  /** 获取锁失败次数 */
  private final LongAdder acquireFailCount = new LongAdder();

  /** 释放锁次数 */
  private final LongAdder releaseCount = new LongAdder();

  /** 累计等待时间（毫秒） */
  private final AtomicLong totalWaitTimeMillis = new AtomicLong();

  /** 累计持有时间（毫秒） */
  private final AtomicLong totalHoldTimeMillis = new AtomicLong();

  /** 锁竞争次数 */
  private final LongAdder competitionCount = new LongAdder();

  /** 当前活跃锁数量 */
  private final AtomicInteger activeLocks = new AtomicInteger(0);

  /** 锁超时次数 */
  private final LongAdder lockTimeoutCount = new LongAdder();

  /** 看门狗续期次数 */
  private final LongAdder watchdogRenewCount = new LongAdder();

  /** 幂等命中次数 */
  private final LongAdder idempotentHitCount = new LongAdder();

  /**
   * Micrometer 指标收集器（可选，当 classpath 存在 MeterRegistry 时设置）
   *
   * <p>使用独立顶级类 {@link LockMicrometerCollector}，通过 {@code @ConditionalOnClass} 控制加载，避免编译期硬依赖。
   */
  private volatile LockMicrometerCollector micrometerCollector;

  /**
   * 记录获取锁成功
   *
   * @param waitTimeMillis 等待时间（毫秒）
   * @param lockType 锁类型
   */
  public void recordAcquireSuccess(long waitTimeMillis, String lockType) {
    acquireSuccessCount.increment();
    totalWaitTimeMillis.addAndGet(waitTimeMillis);
    if (micrometerCollector != null) {
      micrometerCollector.recordAcquireSuccess(waitTimeMillis, lockType);
    }
  }

  /**
   * 记录获取锁失败
   *
   * @param lockType 锁类型
   */
  public void recordAcquireFail(String lockType) {
    acquireFailCount.increment();
    if (micrometerCollector != null) {
      micrometerCollector.recordAcquireFail(lockType);
    }
  }

  /**
   * 记录释放锁
   *
   * @param holdTimeMillis 持有时间（毫秒）
   * @param lockType 锁类型
   */
  public void recordRelease(long holdTimeMillis, String lockType) {
    releaseCount.increment();
    totalHoldTimeMillis.addAndGet(holdTimeMillis);
    if (micrometerCollector != null) {
      micrometerCollector.recordRelease(holdTimeMillis, lockType);
    }
  }

  // --- 新增指标采集方法 ---

  /**
   * 记录锁竞争次数
   *
   * @param lockType 锁类型
   * @param lockKey 锁键
   */
  public void recordCompetition(String lockType, String lockKey) {
    competitionCount.increment();
  }

  /**
   * 记录锁等待时间（带锁类型标签）
   *
   * @param waitTimeMillis 等待时间（毫秒）
   * @param lockType 锁类型
   */
  public void recordWaitDuration(long waitTimeMillis, String lockType) {
    if (micrometerCollector != null) {
      micrometerCollector.recordWaitDuration(waitTimeMillis, lockType);
    }
  }

  /** 增加活跃锁数量 */
  public void incrementActiveLocks() {
    activeLocks.incrementAndGet();
    if (micrometerCollector != null) {
      micrometerCollector.incrementActiveLocks();
    }
  }

  /** 减少活跃锁数量 */
  public void decrementActiveLocks() {
    activeLocks.decrementAndGet();
    if (micrometerCollector != null) {
      micrometerCollector.decrementActiveLocks();
    }
  }

  /**
   * 记录锁超时次数（带锁类型标签）
   *
   * @param lockType 锁类型
   */
  public void recordLockTimeout(String lockType) {
    lockTimeoutCount.increment();
    if (micrometerCollector != null) {
      micrometerCollector.recordLockTimeout(lockType);
    }
  }

  /**
   * 记录看门狗续期次数（带锁类型标签）
   *
   * @param lockType 锁类型
   */
  public void recordWatchdogRenew(String lockType) {
    watchdogRenewCount.increment();
    if (micrometerCollector != null) {
      micrometerCollector.recordWatchdogRenew(lockType);
    }
  }

  /** 记录幂等命中次数 */
  public void recordIdempotentHit() {
    idempotentHitCount.increment();
  }

  /**
   * 绑定 Micrometer MeterRegistry，启用 Prometheus 指标采集
   *
   * <p>此方法仅在 MeterRegistry 存在于 classpath 时可调用， 由 {@link LockMetricsConfiguration} 自动配置类条件化调用。
   *
   * @param meterRegistry Micrometer MeterRegistry 实例
   */
  public void bindMeterRegistry(MeterRegistry meterRegistry) {
    this.micrometerCollector = new LockMicrometerCollector(meterRegistry);
  }

  /**
   * 获取锁成功总次数
   *
   * @return 成功次数
   */
  public long getAcquireSuccessCount() {
    return acquireSuccessCount.sum();
  }

  /**
   * 获取锁失败总次数
   *
   * @return 失败次数
   */
  public long getAcquireFailCount() {
    return acquireFailCount.sum();
  }

  /**
   * 获取释放锁总次数
   *
   * @return 释放次数
   */
  public long getReleaseCount() {
    return releaseCount.sum();
  }

  /**
   * 获取平均等待时间（毫秒）
   *
   * @return 平均等待时间
   */
  public double getAverageWaitTimeMillis() {
    long count = acquireSuccessCount.sum();
    return count == 0 ? 0 : (double) totalWaitTimeMillis.get() / count;
  }

  /**
   * 获取平均持有时间（毫秒）
   *
   * @return 平均持有时间
   */
  public double getAverageHoldTimeMillis() {
    long count = releaseCount.sum();
    return count == 0 ? 0 : (double) totalHoldTimeMillis.get() / count;
  }

  /**
   * 获取锁竞争总次数
   *
   * @return 竞争次数
   */
  public long getCompetitionCount() {
    return competitionCount.sum();
  }

  /**
   * 获取当前活跃锁数量
   *
   * @return 活跃锁数量
   */
  public int getActiveLocks() {
    return activeLocks.get();
  }

  /**
   * 获取锁超时总次数
   *
   * @return 超时次数
   */
  public long getLockTimeoutCount() {
    return lockTimeoutCount.sum();
  }

  /**
   * 获取看门狗续期总次数
   *
   * @return 续期次数
   */
  public long getWatchdogRenewCount() {
    return watchdogRenewCount.sum();
  }

  /**
   * 获取幂等命中总次数
   *
   * @return 幂等命中次数
   */
  public long getIdempotentHitCount() {
    return idempotentHitCount.sum();
  }

  @Override
  public String toString() {
    return String.format(
        "LockMetrics{success=%d, fail=%d, release=%d, competition=%d, active=%d, timeout=%d, renew=%d, idempotent=%d, avgWait=%.1fms, avgHold=%.1fms}",
        getAcquireSuccessCount(),
        getAcquireFailCount(),
        getReleaseCount(),
        getCompetitionCount(),
        getActiveLocks(),
        getLockTimeoutCount(),
        getWatchdogRenewCount(),
        getIdempotentHitCount(),
        getAverageWaitTimeMillis(),
        getAverageHoldTimeMillis());
  }
}
