package com.njydsz.common.util.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

    // 预缓存 Micrometer 指标实例，避免热路径重复做 tag 拼接与 registry 查找
    private io.micrometer.core.instrument.Timer taskDurationTimer;
    private io.micrometer.core.instrument.Counter slowTaskCounter;
    private io.micrometer.core.instrument.Counter failedTaskCounter;
    private io.micrometer.core.instrument.Counter rejectedCounter;

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
     @return 处理结果
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
     * @param poolName 线程池名称
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 空闲线程存活时间
     * @param unit 时间单位
     * @param workQueue 工作队列
     * @param threadFactory 线程工厂
     * @param handler 拒绝策略
     @return 处理结果
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

        rejectedCounter = io.micrometer.core.instrument.Counter.builder("executor.rejected.count")
                .tag("pool.name", poolName)
                .description("累计拒绝任务数")
                .register(meterRegistry);

        failedTaskCounter = io.micrometer.core.instrument.Counter.builder("executor.failed.tasks")
                .tag("pool.name", poolName)
                .description("累计执行失败的任务数")
                .register(meterRegistry);

        slowTaskCounter = io.micrometer.core.instrument.Counter.builder("executor.slow.tasks")
                .tag("pool.name", poolName)
                .description("累计慢任务数（耗时超过阈值）")
                .register(meterRegistry);

        taskDurationTimer = io.micrometer.core.instrument.Timer.builder("executor.task.duration")
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
    public <T> java.util.concurrent.Future<T> submit(Callable<T> task) {
        return super.submit(new MeteredCallable<>(task));
    }

    @Override
    public java.util.concurrent.Future<?> submit(Runnable task) {
        return super.submit(new MeteredTask(task));
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
        return super.submit(new MeteredTask(task), result);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // 注意：execute(Runnable) 与 submit(Runnable) 路径的失败统计由
        // MeteredTask/MeteredCallable 内部完成（submit 路径异常被 Future 吞掉，
        // afterExecute 收不到 t，只能由包装器捕获）。此处不再重复计数。
    }

    /**
     * 记录一次任务执行耗时与慢任务检测（execute / submit 共用）。
     *
     * @param startNanos 任务开始时间（System.nanoTime）
     * @param taskName   任务标识（用于慢任务日志）
     */
    private void recordElapsed(long startNanos, String taskName) {
        long elapsedNanos = System.nanoTime() - startNanos;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

        if (micrometerAvailable) {
            taskDurationTimer.record(elapsedNanos, TimeUnit.NANOSECONDS);
        }

        if (elapsedMs > slowTaskThresholdMs) {
            slowTaskCount.incrementAndGet();
            if (micrometerAvailable) {
                slowTaskCounter.increment();
            }
            onSlowTask(taskName, elapsedMs);
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
     @return 处理结果
     */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * 获取累计失败任务数。
     @return 计算结果
     */
    public long getFailedTaskCount() {
        return failedTaskCount.get();
    }

    /**
     * 获取累计慢任务数。
     @return 计算结果
     */
    public long getSlowTaskCount() {
        return slowTaskCount.get();
    }

    /**
     * 获取累计总任务数。
     @return 计算结果
     */
    public long getTotalTaskCount() {
        return totalTaskCount.get();
    }

    /**
     * 获取线程池名称。
     @return 计算结果
     */
    public String getPoolName() {
        return poolName;
    }

    // ==================== 内部包装 ====================

    private RejectedExecutionHandler wrapHandler(RejectedExecutionHandler original) {
        return (r, executor) -> {
            rejectedCount.incrementAndGet();
            if (micrometerAvailable) {
                rejectedCounter.increment();
            }
            LOG.warn("Task rejected by pool [{}], total rejected: {}", poolName, rejectedCount.get());
            original.rejectedExecution(r, executor);
        };
    }

    /**
     * 任务包装器（Runnable）——添加耗时统计、慢任务检测与失败统计。
     *
     * <p>失败统计放在包装器内部而非 afterExecute：submit(Runnable) 路径的异常
     * 会被 Future 吞掉（afterExecute 收不到），只有包装器自身能可靠捕获。
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
            } catch (Throwable t) {
                countFailure(t);
                throw t;
            } finally {
                recordElapsed(startTime, delegate.getClass().getSimpleName());
            }
        }
    }

    /**
     * 任务包装器（Callable）——与 {@link MeteredTask} 等价，覆盖 submit(Callable) 路径。
     */
    private class MeteredCallable<T> implements Callable<T> {
        private final Callable<T> delegate;
        private final long startTime;

        MeteredCallable(Callable<T> delegate) {
            this.delegate = delegate;
            this.startTime = System.nanoTime();
        }

        @Override
        public T call() throws Exception {
            totalTaskCount.incrementAndGet();
            try {
                return delegate.call();
            } catch (Throwable t) {
                countFailure(t);
                throw t;
            } finally {
                recordElapsed(startTime, delegate.getClass().getSimpleName());
            }
        }
    }

    /**
     * 统一的失败统计逻辑（execute / submit 路径共用）。
     * @param t t
     */
    private void countFailure(Throwable t) {
        failedTaskCount.incrementAndGet();
        if (micrometerAvailable) {
            failedTaskCounter.increment();
        }
        onTaskFailed(() -> { /* 无法还原原始任务对象，仅记录异常 */ }, t);
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
