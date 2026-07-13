package com.njydsz.pmis.common.lock.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 分布式锁 Micrometer 指标收集器
 *
 * <p>独立的顶级类，通过 {@code @ConditionalOnClass(MeterRegistry.class)} 控制加载，
 * 避免对 Micrometer 的编译期硬依赖。
 *
 * <p>注册的指标（统一使用点分隔命名，与 ydsz-pmis-common 其他模块保持一致）：
 * <ul>
 *   <li>{@code lock.acquire.total} - 获取锁成功总数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.acquire.failed.total} - 获取锁失败总数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.release.total} - 释放锁总数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.hold.duration} - 锁持有耗时（Timer，标签: lock_type）</li>
 *   <li>{@code lock.competition.count} - 锁竞争次数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.wait.duration} - 锁等待时间分布（Timer，标签: lock_type）</li>
 *   <li>{@code lock.active.locks} - 当前活跃锁数量（Gauge）</li>
 *   <li>{@code lock.timeout.count} - 锁超时次数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.watchdog.renew.count} - 续期次数（Counter，标签: lock_type）</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class LockMicrometerCollector {

    /**
     * 获取锁成功总数指标名称
     */
    private static final String METRIC_ACQUIRE_TOTAL = "lock.acquire.total";
    /**
     * 获取锁失败总数指标名称
     */
    private static final String METRIC_ACQUIRE_FAILED_TOTAL = "lock.acquire.failed.total";
    /**
     * 释放锁总数指标名称
     */
    private static final String METRIC_RELEASE_TOTAL = "lock.release.total";
    /**
     * 锁持有耗时指标名称
     */
    private static final String METRIC_HOLD_DURATION = "lock.hold.duration";
    /**
     * 锁竞争次数指标名称
     */
    private static final String METRIC_COMPETITION_COUNT = "lock.competition.count";
    /**
     * 锁等待时间分布指标名称
     */
    private static final String METRIC_WAIT_DURATION = "lock.wait.duration";
    /**
     * 当前活跃锁数量指标名称
     */
    private static final String METRIC_ACTIVE_LOCKS = "lock.active.locks";
    /**
     * 锁超时次数指标名称
     */
    private static final String METRIC_TIMEOUT_COUNT = "lock.timeout.count";
    /**
     * 续期次数指标名称
     */
    private static final String METRIC_WATCHDOG_RENEW_COUNT = "lock.watchdog.renew.count";
    /**
     * 锁类型标签键
     */
    private static final String TAG_LOCK_TYPE = "lock_type";

    /**
     * Micrometer 指标注册表
     */
    private final MeterRegistry registry;
    /**
     * 活跃锁数量计数器，用于 Gauge 指标
     */
    private final AtomicInteger activeLocksCounter = new AtomicInteger(0);

    /**
     * 构造 Micrometer 指标收集器，注册活跃锁数量 Gauge 指标
     *
     * @param registry Micrometer 指标注册表
     */
    public LockMicrometerCollector(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(METRIC_ACTIVE_LOCKS, activeLocksCounter, AtomicInteger::get)
                .description("Current number of active locks")
                .register(registry);
    }

    // --- 原有指标 ---

    /**
     * 记录获取锁成功（无锁类型标签）
     *
     * @param waitTimeMillis 等待时间（毫秒）
     */
    void recordAcquireSuccess(long waitTimeMillis) {
        Counter.builder(METRIC_ACQUIRE_TOTAL)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Total number of successful lock acquisitions")
                .register(registry)
                .increment();
    }

    /**
     * 记录获取锁成功（带锁类型标签）
     *
     * @param waitTimeMillis 等待时间（毫秒）
     * @param lockType       锁类型
     */
    void recordAcquireSuccess(long waitTimeMillis, String lockType) {
        Counter.builder(METRIC_ACQUIRE_TOTAL)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Total number of successful lock acquisitions")
                .register(registry)
                .increment();
    }

    /**
     * 记录获取锁失败（无锁类型标签）
     */
    void recordAcquireFail() {
        Counter.builder(METRIC_ACQUIRE_FAILED_TOTAL)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Total number of failed lock acquisitions")
                .register(registry)
                .increment();
    }

    /**
     * 记录获取锁失败（带锁类型标签）
     *
     * @param lockType 锁类型
     */
    void recordAcquireFail(String lockType) {
        Counter.builder(METRIC_ACQUIRE_FAILED_TOTAL)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Total number of failed lock acquisitions")
                .register(registry)
                .increment();
    }

    /**
     * 记录释放锁（无锁类型标签）
     *
     * @param holdTimeMillis 持有时间（毫秒）
     */
    void recordRelease(long holdTimeMillis) {
        Counter.builder(METRIC_RELEASE_TOTAL)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Total number of lock releases")
                .register(registry)
                .increment();

        Timer.builder(METRIC_HOLD_DURATION)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Lock hold duration")
                .register(registry)
                .record(Duration.ofMillis(holdTimeMillis));
    }

    /**
     * 记录释放锁（带锁类型标签）
     *
     * @param holdTimeMillis 持有时间（毫秒）
     * @param lockType       锁类型
     */
    void recordRelease(long holdTimeMillis, String lockType) {
        Counter.builder(METRIC_RELEASE_TOTAL)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Total number of lock releases")
                .register(registry)
                .increment();

        Timer.builder(METRIC_HOLD_DURATION)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Lock hold duration")
                .register(registry)
                .record(Duration.ofMillis(holdTimeMillis));
    }

    // --- 新增指标 ---

    /**
     * 记录锁竞争次数
     */
    void recordCompetition() {
        Counter.builder(METRIC_COMPETITION_COUNT)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Total number of lock competitions")
                .register(registry)
                .increment();
    }

    /**
     * 记录锁竞争次数（带锁类型标签）
     */
    void recordCompetition(String lockType, String lockKey) {
        Counter.builder(METRIC_COMPETITION_COUNT)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Total number of lock competitions")
                .register(registry)
                .increment();
    }

    /**
     * 记录锁等待时间
     */
    void recordWaitDuration(long waitTimeMillis) {
        Timer.builder(METRIC_WAIT_DURATION)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Lock wait time distribution")
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofMillis(waitTimeMillis));
    }

    /**
     * 记录锁等待时间（带锁类型标签）
     */
    void recordWaitDuration(long waitTimeMillis, String lockType) {
        Timer.builder(METRIC_WAIT_DURATION)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Lock wait time distribution")
                .publishPercentileHistogram()
                .register(registry)
                .record(Duration.ofMillis(waitTimeMillis));
    }

    /**
     * 增加活跃锁数量
     */
    void incrementActiveLocks() {
        activeLocksCounter.incrementAndGet();
    }

    /**
     * 减少活跃锁数量
     */
    void decrementActiveLocks() {
        activeLocksCounter.decrementAndGet();
    }

    /**
     * 获取当前活跃锁数量
     */
    int getActiveLocks() {
        return activeLocksCounter.get();
    }

    /**
     * 记录锁超时次数
     */
    void recordLockTimeout() {
        Counter.builder(METRIC_TIMEOUT_COUNT)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Total number of lock timeouts")
                .register(registry)
                .increment();
    }

    /**
     * 记录锁超时次数（带锁类型标签）
     */
    void recordLockTimeout(String lockType) {
        Counter.builder(METRIC_TIMEOUT_COUNT)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Total number of lock timeouts")
                .register(registry)
                .increment();
    }

    /**
     * 记录续期次数
     */
    void recordWatchdogRenew() {
        Counter.builder(METRIC_WATCHDOG_RENEW_COUNT)
                .tag(TAG_LOCK_TYPE, "unknown")
                .description("Total number of watchdog renewals")
                .register(registry)
                .increment();
    }

    /**
     * 记录续期次数（带锁类型标签）
     */
    void recordWatchdogRenew(String lockType) {
        Counter.builder(METRIC_WATCHDOG_RENEW_COUNT)
                .tag(TAG_LOCK_TYPE, lockType)
                .description("Total number of watchdog renewals")
                .register(registry)
                .increment();
    }
}
