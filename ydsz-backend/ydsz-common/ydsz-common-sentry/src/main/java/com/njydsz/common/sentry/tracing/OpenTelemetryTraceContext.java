package com.njydsz.common.sentry.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

import com.njydsz.common.sentry.spi.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenTelemetry 追踪上下文
 *
 * <p>基于 OpenTelemetry API 实现，当 OTel SDK 存在时自动接入。
 * 作为 SkyWalking 之外的未来标准追踪方案。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class OpenTelemetryTraceContext implements TraceContext {

    /**
     * 构造函数，初始化 OpenTelemetry 追踪上下文
     */
    public OpenTelemetryTraceContext() {
        log.info("[Sentry] OpenTelemetryTraceContext 初始化完成");
    }

    /**
     * 获取当前 TraceId
     *
     * @return TraceId，若 OpenTelemetry SDK 不可用则返回 null
     */
    @Override
    public String getTraceId() {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            SpanContext spanContext = currentSpan.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getTraceId();
            }
        } catch (Exception e) {
            log.debug("[Sentry] OpenTelemetry getTraceId 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取当前 SpanId
     *
     * @return SpanId，若 OpenTelemetry SDK 不可用则返回 null
     */
    @Override
    public String getSpanId() {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            SpanContext spanContext = currentSpan.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getSpanId();
            }
        } catch (Exception e) {
            log.debug("[Sentry] OpenTelemetry getSpanId 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 判断是否在 OpenTelemetry 追踪链路中
     *
     * @return 是否在追踪链路中
     */
    @Override
    public boolean isTracing() {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            return currentSpan.getSpanContext().isValid();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 注入自定义标签到当前 Span
     *
     * @param key   标签键
     * @param value 标签值
     */
    @Override
    public void tag(String key, String value) {
        try {
            Span currentSpan = Span.fromContext(Context.current());
            currentSpan.setAttribute(key, value);
        } catch (Exception e) {
            log.debug("[Sentry] OpenTelemetry tag 注入失败: key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 获取追踪系统名称
     *
     * @return 固定返回 "opentelemetry"
     */
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
