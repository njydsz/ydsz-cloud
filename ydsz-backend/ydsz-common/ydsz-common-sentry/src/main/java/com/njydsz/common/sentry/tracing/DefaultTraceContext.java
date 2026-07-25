package com.njydsz.common.sentry.tracing;

import java.util.UUID;

import org.slf4j.MDC;

import com.njydsz.common.sentry.spi.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认追踪上下文（降级方案）
 *
 * <p>当 SkyWalking 不可用时使用 UUID 生成 TraceId，通过 MDC 传递。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DefaultTraceContext implements TraceContext {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    public DefaultTraceContext() {
        log.info("[Sentry] DefaultTraceContext 初始化完成（降级模式, UUID TraceId）");
    }

    @Override
    public String getTraceId() {
        String traceId = getMdcValue(MDC_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            setMdcValue(MDC_TRACE_ID, traceId);
        }
        return traceId;
    }

    @Override
    public String getSpanId() {
        String spanId = getMdcValue(MDC_SPAN_ID);
        if (spanId == null || spanId.isEmpty()) {
            spanId = generateSpanId();
            setMdcValue(MDC_SPAN_ID, spanId);
        }
        return spanId;
    }

    @Override
    public boolean isTracing() {
        String traceId = getMdcValue(MDC_TRACE_ID);
        return traceId != null && !traceId.isEmpty();
    }

    @Override
    public void tag(String key, String value) {
        // 降级方案：通过 MDC 传递标签
        setMdcValue("tag_" + key, value);
    }

    @Override
    public String getTracerName() {
        return "default-uuid";
    }

    /**
     * 生成 TraceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 SpanId
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 获取 MDC 值
     */
    private String getMdcValue(String key) {
        try {
            return MDC.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 设置 MDC 值
     */
    private void setMdcValue(String key, String value) {
        try {
            MDC.put(key, value);
        } catch (Exception e) {
            log.debug("[Sentry] MDC put 失败: key={}, err={}", key, e.getMessage());
        }
    }
}
