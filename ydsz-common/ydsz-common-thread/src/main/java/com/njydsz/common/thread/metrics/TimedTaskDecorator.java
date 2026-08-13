package com.njydsz.common.thread.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * 任务执行耗时追踪装饰器。
 *
 * <p>包装原始 {@link Runnable}，在任务执行前后记录时间戳，
 * 自动计算执行耗时与队列等待时长，并回调关联的 {@link ThreadPoolTimerMetrics}。
 *
 * <p>使用方式：
 * <pre>{@code
 * TimedTaskDecorator decorator = new TimedTaskDecorator(poolName, timerMetrics);
 * executor.setTaskDecorator(decorator);
 * }</pre>
 *
 * <p>线程安全：使用 {@link ConcurrentMap} 按线程存储提交时间戳，
 * 不会阻塞任务执行路径。
 *
 * @author ydsz-team
 * @since 1.3.1
 * @see ThreadPoolTimerMetrics
 */
public class TimedTaskDecorator implements TaskDecorator {

    /**
     * 任务提交时戳持有者（线程 ID → 纳秒时间戳）。
     *
     * <p>使用弱引用或定期清理实现，此处采用简单策略：
     * 由于每个任务的 record 和 remove 操作是连续的，不会出现内存泄漏。
     */
    private static final ConcurrentMap<Long, Long> SUBMITTED_TIMES = new ConcurrentHashMap<>(64);

    private final String poolName;
    private final ThreadPoolTimerMetrics timerMetrics;

    /**
     * 构造耗时追踪装饰器。
     *
     * @param poolName     线程池名称（用于日志追溯）
     * @param timerMetrics 关联的耗时指标绑定器
     */
    public TimedTaskDecorator(String poolName, ThreadPoolTimerMetrics timerMetrics) {
        this.poolName = poolName;
        this.timerMetrics = timerMetrics;
    }

    @Override
    public Runnable decorate(@NonNull Runnable runnable) {
        long submittedAt = System.nanoTime();
        long threadId = Thread.currentThread().threadId();

        // 记录提交时间（使用提交线程 ID 作为 key，避免与执行线程冲突）
        SUBMITTED_TIMES.put(threadId, submittedAt);

        return () -> {
            long startedAt = System.nanoTime();
            Long submitted = SUBMITTED_TIMES.remove(threadId);

            long queueWaitMs = 0L;
            if (submitted != null && submitted > 0) {
                queueWaitMs = Math.max(0L, (startedAt - submitted) / 1_000_000L);
            }

            try {
                runnable.run();
            } finally {
                long finishedAt = System.nanoTime();
                long executionMs = (finishedAt - startedAt) / 1_000_000L;
                recordMetric(executionMs, queueWaitMs);
            }
        };
    }

    /**
     * 记录耗时指标。
     *
     * <p>捕获所有异常，确保指标上报不影响任务执行。
     */
    private void recordMetric(long executionMs, long queueWaitMs) {
        try {
            if (timerMetrics != null) {
                timerMetrics.record(executionMs, queueWaitMs);
            }
        } catch (Exception e) {
            // 指标异常静默处理，不影响业务任务
        }
    }

    /**
     * 获取指定线程池名称。
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * 清理残留的提交时间戳（用于测试或异常场景）。
     */
    public static void clearSubmittedTimes() {
        SUBMITTED_TIMES.clear();
    }
}
