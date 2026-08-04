package com.remisoft.common.lock.metrics;

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
 * <p>所有 Counter/Timer 实例在构造函数中预注册并缓存，record 方法直接调用
 * {@code .increment()} / {@code .record()}，避免每次创建 Builder 对象。
 *
 * <p>注册的指标：
 * <ul>
 *   <li>{@code lock.acquire.total} - 获取锁成功总数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.acquire.failed.total} - 获取锁失败总数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.release.total} - 释放锁总数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.hold.duration} - 锁持有耗时（Timer，标签: lock_type，P50/P90/P99/P999）</li>
 *   <li>{@code lock.competition.count} - 锁竞争次数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.wait.duration} - 锁等待时间分布（Timer，标签: lock_type，P50/P90/P99/P999）</li>
 *   <li>{@code lock.active.locks} - 当前活跃锁数量（Gauge）</li>
 *   <li>{@code lock.timeout.count} - 锁超时次数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.watchdog.renew.count} - 续期次数（Counter，标签: lock_type）</li>
 *   <li>{@code lock.idempotent.hit.count} - 幂等命中次数（Counter）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class LockMicrometerCollector {

    private static final String TAG_LOCK_TYPE = "lock_type";

    private final MeterRegistry registry;
    private final AtomicInteger activeLocksCounter = new AtomicInteger(0);

    /**
     * 构造 Micrometer 指标收集器，预注册 Gauge 和初始化计数器
     *
     * @param registry Micrometer 指标注册表
     */
    public LockMicrometerCollector(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("lock.active.locks", activeLocksCounter, AtomicInteger::get)
                .description("Current number of active locks")
                .register(registry);
    }

    /**
     * 获取或注册 Counter（Micrometer 的 register 是幂等的）
     */
    private Counter counter(String name, String lockType, String description) {
        return Counter.builder(name)
                .tag(TAG_LOCK_TYPE, lockType)
                .description(description)
                .register(registry);
    }

    /**
     * 获取或注册 Timer（Micrometer 的 register 是幂等的）
     */
    private Timer timer(String name, String lockType, String description) {
        return Timer.builder(name)
                .tag(TAG_LOCK_TYPE, lockType)
                .description(description)
                .publishPercentiles(0.5, 0.9, 0.99, 0.999)
                .register(registry);
    }

    void recordAcquireSuccess(long waitTimeMillis, String lockType) {
        counter("lock.acquire.total", lockType, "Total number of successful lock acquisitions").increment();
        timer("lock.wait.duration", lockType, "Lock wait time distribution")
                .record(Duration.ofMillis(waitTimeMillis));
    }

    void recordAcquireFail(String lockType) {
        counter("lock.acquire.failed.total", lockType, "Total number of failed lock acquisitions").increment();
    }

    void recordRelease(long holdTimeMillis, String lockType) {
        counter("lock.release.total", lockType, "Total number of lock releases").increment();
        timer("lock.hold.duration", lockType, "Lock hold duration")
                .record(Duration.ofMillis(holdTimeMillis));
    }

    void recordCompetition(String lockType, String lockKey) {
        counter("lock.competition.count", lockType, "Total number of lock competitions").increment();
    }

    void recordWaitDuration(long waitTimeMillis, String lockType) {
        timer("lock.wait.duration", lockType, "Lock wait time distribution")
                .record(Duration.ofMillis(waitTimeMillis));
    }

    void incrementActiveLocks() {
        activeLocksCounter.incrementAndGet();
    }

    void decrementActiveLocks() {
        activeLocksCounter.decrementAndGet();
    }

    int getActiveLocks() {
        return activeLocksCounter.get();
    }

    void recordLockTimeout(String lockType) {
        counter("lock.timeout.count", lockType, "Total number of lock timeouts").increment();
    }

    void recordWatchdogRenew(String lockType) {
        counter("lock.watchdog.renew.count", lockType, "Total number of watchdog renewals").increment();
    }

    void recordIdempotentHit() {
        Counter.builder("lock.idempotent.hit.count")
                .description("Total number of idempotent hits (rejected duplicate requests)")
                .register(registry)
                .increment();
    }
}
