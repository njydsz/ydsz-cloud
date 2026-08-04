package com.remisoft.common.audit.core;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.TimeUnit;
/**
 * 审计模块 Micrometer 指标绑定器
 * <p>
 * 将审计记录器的运行指标暴露到 Micrometer / Prometheus 端点，
 * 支持通过 Grafana 仪表盘监控审计模块的运行状态。
 * </p>
 *
 * <p><b>暴露指标：</b></p>
 * <ul>
 *   <li>{@code audit.queue.size} (Gauge) — 当前队列深度</li>
 *   <li>{@code audit.queue.usage} (Gauge) — 队列使用率（0.0-1.0）</li>
 *   <li>{@code audit.queue.full.count} (Counter) — 队列满触发次数</li>
 *   <li>{@code audit.record.success} (Counter) — 累计成功写入数</li>
 *   <li>{@code audit.record.failure} (Counter) — 累计失败写入数</li>
 *   <li>{@code audit.write.latency} (Timer) — 批量写入延迟</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class AuditMetricsBinder implements MeterBinder {

    /** 审计记录器 */
    private final AuditRecorder auditRecorder;

    /** 累计成功写入数 */
    private final AtomicLong successCount = new AtomicLong(0);

    /** 累计失败写入数 */
    private final AtomicLong failureCount = new AtomicLong(0);

    /** 批量写入延迟 Timer（延迟初始化，绑定到 MeterRegistry 后才可用） */
    private volatile Timer writeLatencyTimer;

    /**
     * 构造审计指标绑定器
     *
     * @param auditRecorder 审计记录器
     */
    public AuditMetricsBinder(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // Gauge: 队列大小
        registry.gauge("audit.queue.size", auditRecorder, recorder -> {
            if (recorder instanceof AsyncAuditRecorder asyncRecorder) {
                return asyncRecorder.getQueueSize();
            }
            return 0.0;
        });

        // Gauge: 队列使用率
        registry.gauge("audit.queue.usage", auditRecorder, recorder -> {
            if (recorder instanceof AsyncAuditRecorder asyncRecorder) {
                return asyncRecorder.getQueueUsageRatio();
            }
            return 0.0;
        });

        // Counter: 队列满触发次数
        registry.gauge("audit.queue.full.count", auditRecorder, recorder -> {
            if (recorder instanceof AsyncAuditRecorder asyncRecorder) {
                return (double) asyncRecorder.getQueueFullWarnCount();
            }
            if (recorder instanceof DisruptorAuditRecorder disruptorRecorder) {
                return (double) disruptorRecorder.getQueueFullWarnCount();
            }
            return 0.0;
        });

        // Counter: 累计成功写入数
        registry.gauge("audit.record.success", successCount, AtomicLong::doubleValue);

        // Counter: 累计失败写入数
        registry.gauge("audit.record.failure", failureCount, AtomicLong::doubleValue);

        // Timer: 批量写入延迟
        writeLatencyTimer = Timer.builder("audit.write.latency")
                .description("审计日志批量写入延迟")
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
    }

    /**
     * 记录一次成功的写入
     *
     * @param count 写入条数
     * @param latencyNanos 写入延迟（纳秒）
     */
    public void recordSuccess(long count, long latencyNanos) {
        successCount.addAndGet(count);
        if (writeLatencyTimer != null) {
            writeLatencyTimer.record(latencyNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 记录一次失败的写入
     *
     * @param count 写入失败的条数
     */
    public void recordFailure(long count) {
        failureCount.addAndGet(count);
    }

    /**
     * 获取累计成功写入数
     *
     * @return 成功数
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取累计失败写入数
     *
     * @return 失败数
     */
    public long getFailureCount() {
        return failureCount.get();
    }
}
