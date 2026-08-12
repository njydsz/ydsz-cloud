package com.njydsz.common.util.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可观测线程池执行器——Micrometer 指标自动注册 + 慢任务检测。
 *
 * <p>在 {@link ThreadPoolExecutor} 基础上提供以下能力：
 * <ul>
 *   <li>Micrometer 指标自动注册（active.threads、pool.size、queue.size、completed.tasks、rejected.count）</li>
 *   <li>任务执行耗时统计（P50/P95/P99 percentile）</li>
 *   <li>慢任务检测（超过阈值时输出 warn 日志，预留 Hook 可接入告警系统）</li>
 *   <li>任务失败计数 + 异常溯源</li>
 * </ul>
 *
 * <p>Tag 体系：{@code pool.name} 区分不同业务池。
 *
 * <p><b>预留未来能力（Hook 方法）：</b>
 * <ul>
 *   <li>{@link #onTaskFailed(Runnable, Throwable)} — 接入告警系统</li>
 *   <li>{@link #onSlowTask(String, long)} — 慢任务告警回调</li>
 *   <li>队列积压告警（队列深度 &gt; highWaterMark 时触发回调）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 *   MeteredThreadPoolExecutor executor = new MeteredThreadPoolExecutor(
 *       "order-process", 8, 16, 60, TimeUnit.SECONDS,
 *       new LinkedBlockingQueue<>(1024),
 *       new NamedThreadFactory("order"),
 *       new ThreadPoolExecutor.CallerRunsPolicy(),
 *       Metrics.globalRegistry
 *   );
 *   // 启用慢任务检测（超过 1s 告警）
 *   executor.enableSlowTaskDetection(1000);
 * }</pre>
 *
 * <p>配合 Prometheus 可直接导出以下指标构建监控面板：
 * <ul>
 *   <li>{@code executor_active_threads{pool_name="order-process"}}</li>
 *   <li>{@code executor_queue_size{pool_name="order-process"}}</li>
 *   <li>{@code executor_rejected_count_total{pool_name="order-process"}}</li>
 *   <li>{@code executor_task_duration_seconds{pool_name="order-process", quantile="0.99"}}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 3.0.0
 */
public class MeteredThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MeteredThreadPoolExecutor.class);

    private final String poolName;

    // 内置指标计数器（不依赖 Micrometer 时也能工作）
    private final AtomicLong rejectedCount = new AtomicLong();
    private final AtomicLong failedTaskCount = new AtomicLong();
    private final AtomicLong slowTaskCount = new AtomicLong();
    private final AtomicLong totalTaskCount = new AtomicLong();

    // Micrometer 指标
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final boolean micrometerAvailable;

    // 慢任务检测阈值（毫秒），Long.MAX_VALUE 表示关闭
    private volatile long slowTaskThresholdMs = Long.MAX_VALUE;

    /**
     * 构造可观测线程池（自动注册 Micrometer 指标）。
     *
     * @param poolName     线程池名称（用于 metrics tag，建议使用英文短横线命名）
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 空闲线程存活时间
     * @param unit 时间单位
     * @param workQueue 等待队列
     * @param threadFactory 线程工厂
     * @param handler 拒绝策略
     * @param meterRegistry Micrometer Registry（为 null 时不注册指标，仍可使用慢任务检测和异常统计）
     */
    public MeteredThreadPoolExecutor(String poolName,
                                      int corePoolSize,
                                      int maximumPoolSize,
                                      long keepAliveTime,
                                      TimeUnit unit,
                                      BlockingQueue<Runnable> workQueue,
                                      ThreadFactory threadFactory,
                                      RejectedExecutionHandler handler,
                                      io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        setRejectedExecutionHandler(wrapHandler(handler));
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.micrometerAvailable = (meterRegistry != null);

        if (micrometerAvailable) {
            registerMicrometerMetrics();
        }
    }

    /**
     * 构造可观测线程池（无 Micrometer，仅基础统计能力）。
     */
    public MeteredThreadPoolExecutor(String poolName,
                                      int corePoolSize,
                                      int maximumPoolSize,
                                      long keepAliveTime,
                                      TimeUnit unit,
                                      BlockingQueue<Runnable> workQueue,
                                      ThreadFactory threadFactory,
                                      RejectedExecutionHandler handler) {
        this(poolName, corePoolSize, maximumPoolSize, keepAliveTime, unit,
             workQueue, threadFactory, handler, null);
    }

    // ==================== Micrometer 指标注册 ====================

    private void registerMicrometerMetrics() {
        io.micrometer.core.instrument.Gauge.builder("executor.active.threads", this, ThreadPoolExecutor::getActiveCount)
                .tag("pool.name", poolName)
                .description("当前执行任务的线程数")
                .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("executor.pool.size", this, c -> c.getPoolSize())
                .tag("pool.name", poolName)
                .description("当前线程池大小")
                .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("executor.queue.size", this, c -> c.getQueue().size())
                .tag("pool.name", poolName)
                .description("当前等待队列大小")
                .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("executor.queue.remaining", this, c -> c.getQueue().remainingCapacity())
                .tag("pool.name", poolName)
                .description("队列剩余容量")
                .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("executor.completed.tasks", this, c -> c.getCompletedTaskCount())
                .tag("pool.name", poolName)
                .description("累计完成任务数")
                .register(meterRegistry);

        io.micrometer.core.instrument.Counter.builder("executor.rejected.count")
                .tag("pool.name", poolName)
                .description("累计拒绝任务数")
                .register(meterRegistry);

        io.micrometer.core.instrument.Counter.builder("executor.failed.tasks")
                .tag("pool.name", poolName)
                .description("累计执行失败的任务数")
                .register(meterRegistry);

        io.micrometer.core.instrument.Counter.builder("executor.slow.tasks")
                .tag("pool.name", poolName)
                .description("累计慢任务数（耗时超过阈值）")
                .register(meterRegistry);

        io.micrometer.core.instrument.Timer.builder("executor.task.duration")
                .tag("pool.name", poolName)
                .description("任务执行耗时分布")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // ==================== 任务包装与指标采集 ====================

    @Override
    public void execute(Runnable command) {
        super.execute(new MeteredTask(command));
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t != null && r instanceof MeteredTask mt) {
            failedTaskCount.incrementAndGet();
            if (micrometerAvailable) {
                meterRegistry.counter("executor.failed.tasks", "pool.name", poolName).increment();
            }
            onTaskFailed(mt.delegate(), t);
        }
    }

    /**
     * 任务执行失败时的回调——子类可覆写以接入告警系统。
     *
     * <p>默认实现输出 error 日志。覆盖此方法可集成 Prometheus Alertmanager、CAT、Squirrel 等。
     *
     * @param originalTask 原始任务（未包装）
     * @param cause        执行异常
     */
    protected void onTaskFailed(Runnable originalTask, Throwable cause) {
        LOG.error("Task execution failed in pool [{}]: {}", poolName, cause.getMessage(), cause);
    }

    /**
     * 慢任务超时时的回调——子类可覆写以接入告警系统。
     *
     * @param taskName    任务名称（如可提取）
     * @param elapsedMs   实际执行耗时（毫秒）
     */
    protected void onSlowTask(String taskName, long elapsedMs) {
        LOG.warn("Slow task detected in pool [{}]: {} took {}ms (threshold: {}ms)",
                poolName, taskName, elapsedMs, slowTaskThresholdMs);
    }

    // ==================== 慢任务检测配置 ====================

    /**
     * 启用慢任务检测。
     *
     * @param thresholdMs 慢任务阈值（毫秒），超过此耗时的任务触发 {@link #onSlowTask} 回调
     */
    public void enableSlowTaskDetection(long thresholdMs) {
        this.slowTaskThresholdMs = thresholdMs;
        LOG.info("Slow task detection enabled for pool [{}]: threshold={}ms", poolName, thresholdMs);
    }

    // ==================== 统计方法 ====================

    /**
     * 获取累计拒绝任务数。
     */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * 获取累计失败任务数。
     */
    public long getFailedTaskCount() {
        return failedTaskCount.get();
    }

    /**
     * 获取累计慢任务数。
     */
    public long getSlowTaskCount() {
        return slowTaskCount.get();
    }

    /**
     * 获取累计总任务数。
     */
    public long getTotalTaskCount() {
        return totalTaskCount.get();
    }

    /**
     * 获取线程池名称。
     */
    public String getPoolName() {
        return poolName;
    }

    // ==================== 内部包装 ====================

    private RejectedExecutionHandler wrapHandler(RejectedExecutionHandler original) {
        return (r, executor) -> {
            rejectedCount.incrementAndGet();
            if (micrometerAvailable) {
                meterRegistry.counter("executor.rejected.count", "pool.name", poolName).increment();
            }
            LOG.warn("Task rejected by pool [{}], total rejected: {}", poolName, rejectedCount.get());
            original.rejectedExecution(r, executor);
        };
    }

    /**
     * 任务包装器——添加耗时统计和慢任务检测。
     */
    private class MeteredTask implements Runnable {
        private final Runnable delegate;
        private final long startTime;

        MeteredTask(Runnable delegate) {
            this.delegate = delegate;
            this.startTime = System.nanoTime();
        }

        Runnable delegate() { return delegate; }

        @Override
        public void run() {
            totalTaskCount.incrementAndGet();
            try {
                delegate.run();
            } finally {
                long elapsedNanos = System.nanoTime() - startTime;
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

                if (micrometerAvailable) {
                    meterRegistry.timer("executor.task.duration", "pool.name", poolName)
                            .record(elapsedNanos, TimeUnit.NANOSECONDS);
                }

                if (elapsedMs > slowTaskThresholdMs) {
                    slowTaskCount.incrementAndGet();
                    if (micrometerAvailable) {
                        meterRegistry.counter("executor.slow.tasks", "pool.name", poolName).increment();
                    }
                    String taskName = delegate.getClass().getSimpleName();
                    onSlowTask(taskName, elapsedMs);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "MeteredThreadPoolExecutor{" +
                "poolName='" + poolName + '\'' +
                ", active=" + getActiveCount() +
                ", poolSize=" + getPoolSize() +
                ", queueSize=" + getQueue().size() +
                ", completed=" + getCompletedTaskCount() +
                ", rejected=" + rejectedCount.get() +
                ", failed=" + failedTaskCount.get() +
                '}';
    }
}
