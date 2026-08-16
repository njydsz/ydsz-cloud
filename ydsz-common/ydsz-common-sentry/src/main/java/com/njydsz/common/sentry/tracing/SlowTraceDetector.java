package com.njydsz.common.sentry.tracing;

import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.TraceContext;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 慢追踪检测器
 *
 * <p>记录业务关键路径的执行耗时，超过阈值时触发慢追踪告警。
 *
 * <p>活跃追踪缓存使用 LRU（最近最少使用）淘汰策略： 当缓存达到上限时自动移除最久未被访问的条目， 避免 FIFO 淘汰可能移除正在使用的活跃条目。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SlowTraceDetector {

  /** 最大活跃追踪数，防止内存泄漏 */
  private static final int MAX_ACTIVE_TRACES = 10000;

  private final MetricsCollector metricsCollector;
  private final long slowThresholdMillis;
  private final TraceContext traceContext;

  /** 慢追踪计数器 */
  private final AtomicLong slowTraceCount = new AtomicLong(0);

  /**
   * 活跃追踪记录缓存（key=traceId|operation, value=startMillis）。
   *
   * <p>使用 access-order {@link LinkedHashMap} + {@link Collections#synchronizedMap} 实现 线程安全的 LRU
   * 缓存；当大小超过 {@link #MAX_ACTIVE_TRACES} 时自动移除最久未访问的条目。
   */
  private final Map<String, Long> activeTraces =
      Collections.synchronizedMap(
          new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
              boolean shouldRemove = size() > MAX_ACTIVE_TRACES;
              if (shouldRemove) {
                log.debug("[Sentry] 活跃追踪数超过上限 LRU 淘汰: key={}, size={}", eldest.getKey(), size());
              }
              return shouldRemove;
            }
          });

  public SlowTraceDetector(
      MetricsCollector metricsCollector, TraceContext traceContext, long slowThresholdMillis) {
    this.metricsCollector = metricsCollector;
    this.traceContext = traceContext;
    this.slowThresholdMillis = slowThresholdMillis;
    log.info("[Sentry] SlowTraceDetector 初始化: threshold={}ms", slowThresholdMillis);
  }

  /**
   * 开始追踪。
   *
   * @param operation 操作名
   */
  public void startTrace(String operation) {
    String traceId = traceContext != null ? traceContext.getTraceId() : null;
    if (traceId != null) {
      String key = traceId + "|" + operation;
      activeTraces.put(key, System.currentTimeMillis());
    }
  }

  /**
   * 结束追踪。
   *
   * @param operation 操作名
   * @param success 是否成功
   */
  public void endTrace(String operation, boolean success) {
    String traceId = traceContext != null ? traceContext.getTraceId() : null;
    if (traceId == null) {
      return;
    }

    String key = traceId + "|" + operation;
    Long startMillis = activeTraces.remove(key);
    if (startMillis == null) {
      return;
    }

    long tookMillis = System.currentTimeMillis() - startMillis;

    // 记录耗时
    if (metricsCollector != null) {
      metricsCollector.recordTimer(
          "ydsz.trace.duration",
          "链路追踪耗时",
          Map.of("operation", operation, "success", String.valueOf(success)),
          Duration.ofMillis(tookMillis));
    }

    // 慢追踪检测
    if (tookMillis > slowThresholdMillis) {
      slowTraceCount.incrementAndGet();
      if (metricsCollector != null) {
        metricsCollector.incrementCounter(
            "ydsz.trace.slow", "慢追踪次数", Map.of("operation", operation), 1);
      }
      log.warn(
          "[Sentry] 慢追踪: operation={}, took={}ms, threshold={}ms, traceId={}",
          operation,
          tookMillis,
          slowThresholdMillis,
          traceId);
    }
  }

  /**
   * 获取慢追踪总数。
   *
   * @return 累计慢追踪数
   */
  public long getSlowTraceCount() {
    return slowTraceCount.get();
  }
}
