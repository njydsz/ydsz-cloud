package com.njydsz.common.sentry.tracing;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 慢追踪检测器
 *
 * <p>记录业务关键路径的执行耗时，超过阈值时触发慢追踪告警。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Slf4j
public class SlowTraceDetector {

    private final MetricsCollector metricsCollector;
    private final long slowThresholdMillis;
    private final TraceContext traceContext;

    /** 慢追踪计数器 */
    private final AtomicLong slowTraceCount = new AtomicLong(0);
    /** 追踪记录缓存（key=traceId|operation, value=startMillis） */
    private final ConcurrentHashMap<String, Long> activeTraces = new ConcurrentHashMap<>();
    /** 最大活跃追踪数，防止内存泄漏 */
    private static final int MAX_ACTIVE_TRACES = 10000;

    public SlowTraceDetector(MetricsCollector metricsCollector, TraceContext traceContext,
                             long slowThresholdMillis) {
        this.metricsCollector = metricsCollector;
        this.traceContext = traceContext;
        this.slowThresholdMillis = slowThresholdMillis;
        log.info("[Sentry] SlowTraceDetector 初始化: threshold={}ms", slowThresholdMillis);
    }

    /**
     * 开始追踪
     *
     * @param operation 操作名
     */
    public void startTrace(String operation) {
        String traceId = traceContext != null ? traceContext.getTraceId() : null;
        if (traceId != null) {
            String key = traceId + "|" + operation;
            // 防止无限增长：超过上限时移除最早的条目
            if (activeTraces.size() >= MAX_ACTIVE_TRACES) {
                String oldestKey = activeTraces.keys().nextElement();
                activeTraces.remove(oldestKey);
                log.debug("[Sentry] 活跃追踪数超过上限 {}, 移除最早条目: {}", MAX_ACTIVE_TRACES, oldestKey);
            }
            activeTraces.put(key, System.currentTimeMillis());
        }
    }

    /**
     * 结束追踪
     *
     * @param operation 操作名
     * @param success   是否成功
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
            metricsCollector.recordTimer("ydsz.trace.duration",
                    "链路追踪耗时",
                    Map.of("operation", operation, "success", String.valueOf(success)),
                    Duration.ofMillis(tookMillis));
        }

        // 慢追踪检测
        if (tookMillis > slowThresholdMillis) {
            slowTraceCount.incrementAndGet();
            if (metricsCollector != null) {
                metricsCollector.incrementCounter("ydsz.trace.slow",
                        "慢追踪次数",
                        Map.of("operation", operation),
                        1);
            }
            log.warn("[Sentry] 慢追踪: operation={}, took={}ms, threshold={}ms, traceId={}",
                    operation, tookMillis, slowThresholdMillis, traceId);
        }
    }

    /**
     * 获取慢追踪总数
     */
    public long getSlowTraceCount() {
        return slowTraceCount.get();
    }
}
