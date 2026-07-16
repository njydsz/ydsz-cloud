package com.njydsz.pmis.common.sentry.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import com.njydsz.pmis.common.sentry.spi.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenTelemetry 追踪上下文
 *
 * <p>基于 OpenTelemetry API 实现，当 OTel SDK 存在时自动接入。
 * 作为 SkyWalking 之外的未来标准追踪方案。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class OpenTelemetryTraceContext implements TraceContext {

    public OpenTelemetryTraceContext() {
        log.info("[Sentry] OpenTelemetryTraceContext 初始化完成");
    }

    @Override
    public String getTraceId() {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            io.opentelemetry.api.trace.SpanContext spanContext = currentSpan.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getTraceId();
            }
        } catch (Exception e) {
            log.debug("[Sentry] OpenTelemetry getTraceId 失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String getSpanId() {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            io.opentelemetry.api.trace.SpanContext spanContext = currentSpan.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getSpanId();
            }
        } catch (Exception e) {
            log.debug("[Sentry] OpenTelemetry getSpanId 失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isTracing() {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            return currentSpan.getSpanContext().isValid();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void tag(String key, String value) {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            currentSpan.setAttribute(key, value);
        } catch (Exception e) {
            log.debug("[Sentry] OpenTelemetry tag 注入失败: key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public String getTracerName() {
        return "opentelemetry";
    }

    /**
     * 检测 OpenTelemetry SDK 是否可用
     */
    public static boolean isAvailable() {
        try {
            return GlobalOpenTelemetry.get() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
