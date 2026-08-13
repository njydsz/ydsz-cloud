package com.njydsz.common.lock.metrics;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>所有 Counter/Timer 实例在首次使用时懒加载并缓存，record 方法直接调用
 * 缓存实例的 {@code .increment()} / {@code .record()}，避免每次创建 Builder 对象。
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
 * @author ydsz-team
 * @since 1.0.0
 */
public class LockMicrometerCollector {

    private static final String TAG_LOCK_TYPE = "lock_type";

    /** 锁键类别标签（低基数，避免指标标签膨胀） */
    private static final String TAG_LOCK_CATEGORY = "lock_category";

    /** Timer 发布的分位数（P50/P90/P99/P999） */
    private static final double[] PUBLISH_PERCENTILES = {0.5, 0.9, 0.99, 0.999};

    private final MeterRegistry registry;
    private final AtomicInteger activeLocksCounter = new AtomicInteger(0);

    /** 锁键分类提取器（用于降低指标标签基数） */
    private final LockKeyCategoryExtractor categoryExtractor;

    /** Counter 缓存，避免每次 record 创建 Builder */
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    /** Timer 缓存，避免每次 record 创建 Builder */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * 构造 Micrometer 指标收集器，预注册 Gauge 和初始化计数器
     *
     * @param registry Micrometer 指标注册表
     */
    public LockMicrometerCollector(MeterRegistry registry) {
        this(registry, LockKeyCategoryExtractor.DEFAULT);
    }

    /**
     * 构造 Micrometer 指标收集器（带类别提取器）
     *
     * @param registry           Micrometer 指标注册表
     * @param categoryExtractor  锁键分类提取器
     */
    public LockMicrometerCollector(MeterRegistry registry, LockKeyCategoryExtractor categoryExtractor) {
        this.registry = registry;
        this.categoryExtractor = categoryExtractor;
        Gauge.builder("lock.active.locks", activeLocksCounter, AtomicInteger::get)
                .description("Current number of active locks")
                .register(registry);
    }

    /**
     * 获取或缓存 Counter（懒加载，Micrometer 的 Counter.Builder 创建有一定开销）
     *
     * @param name        指标名称
     * @param lockType    锁类型标签
     * @param description 指标描述
     * @return Counter 实例
     */
    private Counter counter(String name, String lockType, String description) {
        String cacheKey = name + "|" + lockType;
        return counterCache.computeIfAbsent(cacheKey, k ->
                Counter.builder(name)
                        .tag(TAG_LOCK_TYPE, lockType)
                        .description(description)
                        .register(registry));
    }

    /**
     * 获取或缓存 Timer（懒加载，Micrometer 的 Timer.Builder 创建有一定开销）
     *
     * @param name        指标名称
     * @param lockType    锁类型标签
     * @param description 指标描述
     * @return Timer 实例
     */
    private Timer timer(String name, String lockType, String description) {
        String cacheKey = name + "|" + lockType;
        return timerCache.computeIfAbsent(cacheKey, k ->
                Timer.builder(name)
                        .tag(TAG_LOCK_TYPE, lockType)
                        .description(description)
                        .publishPercentiles(PUBLISH_PERCENTILES)
                        .register(registry));
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
        String category = categoryExtractor.extractCategory(lockKey);
        String cacheKey = "lock.competition.count|" + lockType + "|" + category;
        counterCache.computeIfAbsent(cacheKey, k ->
                io.micrometer.core.instrument.Counter.builder("lock.competition.count")
                        .tag(TAG_LOCK_TYPE, lockType)
                        .tag(TAG_LOCK_CATEGORY, category)
                        .description("Total number of lock competitions by lock category")
                        .register(registry)
        ).increment();
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
        counterCache.computeIfAbsent("lock.idempotent.hit.count|", k ->
                io.micrometer.core.instrument.Counter.builder("lock.idempotent.hit.count")
                        .description("Total number of idempotent hits (rejected duplicate requests)")
                        .register(registry)
        ).increment();
    }
}
