package com.njydsz.common.sentry.tracing.otel;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Span 错误事件处理器
 *
 * <p>在 Span 结束时，如果检测到以下情况，自动注入告警事件：
 * <ul>
 *   <li>Span 状态为 ERROR 且未携带 ydsz.error.code 属性</li>
 *   <li>HTTP 状态码 5xx 但 Span 状态仍为 OK（未正确标记）</li>
 *   <li>耗时超过指定阈值（已配 TailSamplingSpanProcessor.slowRequest，此处仅补齐告警）</li>
 * </ul>
 *
 * <p>业务方可注册 {@link ErrorEventListener} 接收告警事件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ErrorEventSpanProcessor implements SpanProcessor {

    private final ErrorEventConfig config;
    private final java.util.List<ErrorEventListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public ErrorEventSpanProcessor(ErrorEventConfig config) {
        this.config = config;
        log.info("[Sentry] ErrorEventSpanProcessor 初始化，slowThreshold={}ms",
                config.getSlowThresholdMillis());
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // no-op
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        try {
            ErrorEvent event = evaluate(span);
            if (event != null) {
                notifyListeners(event);
            }
        } catch (Exception e) {
            log.debug("[Sentry] ErrorEvent 评估失败: {}", e.getMessage());
        }
    }

    /**
     * 评估 Span 异常
     */
    private ErrorEvent evaluate(ReadableSpan span) {
        // 1) HTTP 5xx 但未标记为 ERROR
        Long status = span.getAttribute(OtelSemConv.HTTP_RESPONSE_STATUS_CODE);
        boolean isServerError = status != null && status >= 500 && status < 600;
        boolean isError = span.getStatus().getCode() == io.opentelemetry.api.trace.StatusCode.ERROR;

        if (isServerError || isError) {
            String errorCode = span.getAttribute(OtelSemConv.YDSZ_ERROR_CODE);
            return new ErrorEvent(span.getSpanContext().getTraceId(),
                    span.getSpanContext().getSpanId(),
                    span.getName(),
                    isServerError ? ErrorEvent.Reason.SERVER_ERROR : ErrorEvent.Reason.SPAN_ERROR,
                    errorCode != null ? errorCode : (isServerError ? "HTTP_" + status : "UNCLASSIFIED"),
                    span.getStatus().getDescription(),
                    span.getLatencyNanos() / 1_000_000L,
                    span.getKind());
        }

        // 2) 慢请求
        if (config.getSlowThresholdMillis() > 0) {
            long durationMs = span.getLatencyNanos() / 1_000_000L;
            if (durationMs > config.getSlowThresholdMillis()
                    && span.getKind() != SpanKind.CLIENT
                    && span.getKind() != SpanKind.PRODUCER) {
                return new ErrorEvent(span.getSpanContext().getTraceId(),
                        span.getSpanContext().getSpanId(),
                        span.getName(),
                        ErrorEvent.Reason.SLOW,
                        "SLOW_SPAN",
                        "耗时 " + durationMs + "ms 超过阈值 " + config.getSlowThresholdMillis() + "ms",
                        durationMs,
                        span.getKind());
            }
        }
        return null;
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    @Override
    public void close() {
        // no-op
    }

    /**
     * 注册错误事件监听器
     */
    public void addListener(ErrorEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void notifyListeners(ErrorEvent event) {
        for (ErrorEventListener l : listeners) {
            try {
                l.onErrorEvent(event);
            } catch (Exception ignored) {
                // 监听器异常不影响主流程
            }
        }
    }

    // ============================================================================
    // 配置
    // ============================================================================

    @Data
    public static class ErrorEventConfig {
        /** 慢 Span 阈值（毫秒） */
        private long slowThresholdMillis = 3000;
    }

    // ============================================================================
    // 事件
    // ============================================================================

    /**
     * 错误事件
     */
    @Data
    public static class ErrorEvent {
        public enum Reason { SPAN_ERROR, SERVER_ERROR, SLOW }

        private final String traceId;
        private final String spanId;
        private final String spanName;
        private final Reason reason;
        private final String errorCode;
        private final String message;
        private final long durationMillis;
        private final SpanKind kind;
        private final long timestamp = System.currentTimeMillis();

        public ErrorEvent(String traceId, String spanId, String spanName, Reason reason,
                          String errorCode, String message, long durationMillis, SpanKind kind) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.spanName = spanName;
            this.reason = reason;
            this.errorCode = errorCode;
            this.message = message;
            this.durationMillis = durationMillis;
            this.kind = kind;
        }
    }

    /**
     * 错误事件监听器
     */
    @FunctionalInterface
    public interface ErrorEventListener {
        void onErrorEvent(ErrorEvent event);
    }
}
