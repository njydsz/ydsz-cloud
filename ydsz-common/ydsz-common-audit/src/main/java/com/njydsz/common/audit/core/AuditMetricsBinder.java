package com.njydsz.common.audit.core;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 审计模块 Micrometer 指标绑定器
 *
 * <p>将审计记录器的运行指标暴露到 Micrometer / Prometheus 端点， 支持通过 Grafana 仪表盘监控审计模块的运行状态。
 *
 * <p><b>暴露指标：</b>
 *
 * <ul>
 *   <li>{@code audit.queue.size} (Gauge) — 当前队列深度
 *   <li>{@code audit.queue.usage} (Gauge) — 队列使用率（0.0-1.0）
 *   <li>{@code audit.queue.full.count} (Counter) — 队列满触发次数
 *   <li>{@code audit.record.success} (Counter) — 累计成功写入数
 *   <li>{@code audit.record.failure} (Counter) — 累计失败写入数
 *   <li>{@code audit.write.latency} (Timer) — 批量写入延迟
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class AuditMetricsBinder implements MeterBinder {

  /** 指标名称常量 */
  private static final String METRIC_QUEUE_SIZE = "audit.queue.size";

  private static final String METRIC_QUEUE_USAGE = "audit.queue.usage";

  private static final String METRIC_QUEUE_FULL_COUNT = "audit.queue.full.count";

  private static final String METRIC_RECORD_SUCCESS = "audit.record.success";

  private static final String METRIC_RECORD_FAILURE = "audit.record.failure";

  private static final String METRIC_WRITE_LATENCY = "audit.write.latency";

  /** 审计记录器 */
  private final AuditRecorder auditRecorder;

  /** 累计成功写入数 Counter */
  private Counter successCounter;

  /** 累计失败写入数 Counter */
  private Counter failureCounter;

  /** 批量写入延迟 Timer */
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
    registry.gauge(
        METRIC_QUEUE_SIZE,
        auditRecorder,
        recorder -> {
          if (recorder instanceof AsyncAuditRecorder asyncRecorder) {
            return (double) asyncRecorder.getQueueSize();
          }
          return 0.0;
        });

    // Gauge: 队列使用率
    registry.gauge(
        METRIC_QUEUE_USAGE,
        auditRecorder,
        recorder -> {
          if (recorder instanceof AsyncAuditRecorder asyncRecorder) {
            return asyncRecorder.getQueueUsageRatio();
          }
          return 0.0;
        });

    // Counter: 队列满触发次数
    Counter.builder(METRIC_QUEUE_FULL_COUNT)
        .description("审计队列满触发次数")
        .register(registry)
        .increment(getInitialQueueFullCount());

    // Counter: 累计成功写入数
    successCounter =
        Counter.builder(METRIC_RECORD_SUCCESS).description("审计日志累计成功写入数").register(registry);

    // Counter: 累计失败写入数
    failureCounter =
        Counter.builder(METRIC_RECORD_FAILURE).description("审计日志累计失败写入数").register(registry);

    // Timer: 批量写入延迟
    writeLatencyTimer =
        Timer.builder(METRIC_WRITE_LATENCY)
            .description("审计日志批量写入延迟")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
  }

  /**
   * 获取初始队列满计数（用于 Counter 初始值设置）
   *
   * @return 初始计数值
   */
  private double getInitialQueueFullCount() {
    if (auditRecorder instanceof AsyncAuditRecorder asyncRecorder) {
      return (double) asyncRecorder.getQueueFullWarnCount();
    }
    return 0.0;
  }

  /**
   * 记录一次成功的写入
   *
   * @param count 写入条数
   * @param latencyNanos 写入延迟（纳秒）
   */
  public void recordSuccess(long count, long latencyNanos) {
    if (successCounter != null) {
      successCounter.increment(count);
    }
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
    if (failureCounter != null) {
      failureCounter.increment(count);
    }
  }

  /**
   * 获取累计成功写入数（用于健康检查）
   *
   * @return 成功数，若 Counter 未初始化返回 0
   */
  public long getSuccessCount() {
    return successCounter != null ? (long) successCounter.count() : 0L;
  }

  /**
   * 获取累计失败写入数（用于健康检查）
   *
   * @return 失败数，若 Counter 未初始化返回 0
   */
  public long getFailureCount() {
    return failureCounter != null ? (long) failureCounter.count() : 0L;
  }
}
