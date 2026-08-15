package com.njydsz.common.thread.metrics;

import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * 任务执行耗时追踪装饰器。
 *
 * <p>包装原始 {@link Runnable}，在任务执行前后记录时间戳，自动计算执行耗时与队列等待时长，
 * 并回调关联的 {@link ThreadPoolTimerMetrics}。
 *
 * <p>使用方式：
 * <pre>{@code
 * TimedTaskDecorator decorator = new TimedTaskDecorator(poolName, slowTaskThresholdMs, timerMetrics);
 * executor.setTaskDecorator(decorator);
 * }</pre>
 *
 * <p>线程安全：使用不可变包装对象传递时间戳，无跨任务串扰风险，
 * 避免全局 {@code ConcurrentMap} 在高并发场景下因 threadId 复用导致的数据污染。
 *
 * @author ydsz-team
 * @since 1.4.0
 * @see ThreadPoolTimerMetrics
 */
public class TimedTaskDecorator implements TaskDecorator {

    private final String poolName;
    private final long slowTaskThresholdMs;
    private final ThreadPoolTimerMetrics timerMetrics;

    /**
     * 构造耗时追踪装饰器。
     *
     * @param poolName            线程池名称（用于日志追溯与慢任务标记）
     * @param slowTaskThresholdMs 慢任务阈值（毫秒），≥ 100
     * @param timerMetrics        关联的耗时指标绑定器
     */
    public TimedTaskDecorator(String poolName, long slowTaskThresholdMs,
            ThreadPoolTimerMetrics timerMetrics) {
        this.poolName = poolName;
        this.slowTaskThresholdMs = slowTaskThresholdMs;
        this.timerMetrics = timerMetrics;
    }

    @Override
    public Runnable decorate(@NonNull Runnable runnable) {
        long submittedAt = System.nanoTime();
        return new TimedRunnable(submittedAt, runnable);
    }

    /**
     * 获取指定线程池名称。
     *
     * @return 线程池名称
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * 获取慢任务阈值。
     *
     * @return 慢任务阈值（毫秒）
     */
    public long getSlowTaskThresholdMs() {
        return slowTaskThresholdMs;
    }

    /**
     * 可执行包装对象，携带任务提交时间戳。
     *
     * <p>不可变设计确保装饰后的任务可跨线程安全传递，无竞态条件。
     */
    private final class TimedRunnable implements Runnable {

        private final long submittedAt;
        private final Runnable delegate;

        TimedRunnable(long submittedAt, Runnable delegate) {
            this.submittedAt = submittedAt;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            long startedAt = System.nanoTime();
            long queueWaitMs = Math.max(0L, (startedAt - submittedAt) / 1_000_000L);

            try {
                delegate.run();
            } finally {
                long finishedAt = System.nanoTime();
                long executionMs = (finishedAt - startedAt) / 1_000_000L;
                recordMetric(executionMs, queueWaitMs);
            }
        }
    }

    /**
     * 记录耗时指标。
     *
     * <p>捕获所有异常，确保指标上报不影响业务任务。
     */
    private void recordMetric(long executionMs, long queueWaitMs) {
        try {
            if (timerMetrics != null) {
                timerMetrics.record(executionMs, queueWaitMs, slowTaskThresholdMs, poolName);
            }
        } catch (Exception e) {
            // 指标异常静默处理，不影响业务任务
        }
    }
}
