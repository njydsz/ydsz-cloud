package com.njydsz.common.thread.metrics;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 线程池任务耗时与排队时长 Micrometer 指标绑定器。
 *
 * <p>暴露以下三类指标（均按 {@code pool.name} 打标）：
 * <ul>
 *   <li>{@code <prefix>.execution} - 任务执行耗时 Timer（P50/P95/P99）</li>
 *   <li>{@code <prefix>.queue.wait} - 任务在队列中等待时长 Timer</li>
 *   <li>{@code <prefix>.slow.tasks} - 慢任务累计计数（耗时超过 {@link #slowTaskThresholdMs}）</li>
 * </ul>
 *
 * <p>实现 {@link SmartInitializingSingleton}，在所有单例 Bean 初始化完成后自动为
 * 匹配的 {@link ThreadPoolTaskExecutor} 安装 {@link TimedTaskDecorator}，
 * 解决 Bean 创建时序问题。
 *
 * @author ydsz-team
 * @since 1.3.1
 * @see TimedTaskDecorator
 */
public class ThreadPoolTimerMetrics implements MeterBinder, SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolTimerMetrics.class);

    private static final String METRIC_EXECUTION = ".execution";
    private static final String METRIC_QUEUE_WAIT = ".queue.wait";
    private static final String METRIC_SLOW_TASKS = ".slow.tasks";

    /**
     * 慢任务默认阈值：任务执行耗时超过 5 秒判定为慢任务。
     */
    public static final long DEFAULT_SLOW_TASK_THRESHOLD_MS = 5_000L;

    private final String poolName;
    private final String metricPrefix;
    private final Iterable<Tag> tags;
    private final long slowTaskThresholdMs;

    private ApplicationContext applicationContext;
    private Timer executionTimer;
    private Timer queueWaitTimer;
    private Counter slowTaskCounter;

    /**
     * 构造绑定器，使用默认慢任务阈值（5 秒）。
     *
     * @param poolName     线程池名称
     * @param metricPrefix Micrometer 指标前缀
     */
    public ThreadPoolTimerMetrics(String poolName, String metricPrefix) {
        this(poolName, metricPrefix, Tags.empty(), DEFAULT_SLOW_TASK_THRESHOLD_MS);
    }

    /**
     * 构造绑定器（完整参数）。
     *
     * @param poolName           线程池名称
     * @param metricPrefix       Micrometer 指标前缀
     * @param tags               附加标签
     * @param slowTaskThresholdMs 慢任务耗时阈值（毫秒），必须 > 0
     */
    public ThreadPoolTimerMetrics(String poolName, String metricPrefix,
                                   Iterable<Tag> tags, long slowTaskThresholdMs) {
        this.poolName = poolName;
        this.metricPrefix = metricPrefix != null ? metricPrefix : ThreadPoolMetrics.DEFAULT_METRIC_PREFIX;
        this.tags = tags != null ? tags : Tags.empty();
        this.slowTaskThresholdMs = slowTaskThresholdMs > 0 ? slowTaskThresholdMs : DEFAULT_SLOW_TASK_THRESHOLD_MS;
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        executionTimer = Timer.builder(metricPrefix + METRIC_EXECUTION)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("任务执行耗时")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        queueWaitTimer = Timer.builder(metricPrefix + METRIC_QUEUE_WAIT)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("任务在队列中等待时长")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        slowTaskCounter = Counter.builder(metricPrefix + METRIC_SLOW_TASKS)
                .tags(Tags.concat(tags, "pool.name", poolName))
                .description("慢任务累计计数（耗时超过 " + slowTaskThresholdMs + "ms）")
                .register(registry);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 所有单例初始化完成后，自动为匹配的线程池安装耗时追踪装饰器。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (applicationContext == null) {
            return;
        }
        Map<String, ThreadPoolTaskExecutor> executors = applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : executors.entrySet()) {
            String beanName = entry.getKey();
            // 匹配：beanName 以 poolName + "Executor" 结尾
            if (beanName.endsWith(poolName + "Executor")) {
                installDecorator(entry.getValue());
                return;
            }
        }
        log.debug("[ThreadPoolTimerMetrics] 未找到匹配的线程池 [{}]，耗时指标不会生效", poolName);
    }

    /**
     * 安装耗时追踪装饰器到指定的线程池。
     */
    private void installDecorator(ThreadPoolTaskExecutor executor) {
        try {
            TimedTaskDecorator decorator = new TimedTaskDecorator(poolName, this);
            executor.setTaskDecorator(decorator);
            log.info("[ThreadPoolTimerMetrics] 已为线程池 [{}] 安装耗时追踪装饰器", poolName);
        } catch (Exception e) {
            log.warn("[ThreadPoolTimerMetrics] 安装耗时追踪装饰器失败 (pool={}): {}", poolName, e.getMessage());
        }
    }

    /**
     * 记录一次任务执行。
     *
     * <p>由 {@link TimedTaskDecorator} 在任务完成时回调。
     *
     * @param executionDurationMs  任务执行耗时（毫秒）
     * @param queueWaitDurationMs  任务在队列中等待时长（毫秒）
     */
    public void record(long executionDurationMs, long queueWaitDurationMs) {
        if (executionTimer != null) {
            executionTimer.record(executionDurationMs, TimeUnit.MILLISECONDS);
        }
        if (queueWaitTimer != null && queueWaitDurationMs > 0) {
            queueWaitTimer.record(queueWaitDurationMs, TimeUnit.MILLISECONDS);
        }
        if (slowTaskCounter != null && executionDurationMs > slowTaskThresholdMs) {
            slowTaskCounter.increment();
        }
    }

    public String getPoolName() {
        return poolName;
    }

    public long getSlowTaskThresholdMs() {
        return slowTaskThresholdMs;
    }
}
