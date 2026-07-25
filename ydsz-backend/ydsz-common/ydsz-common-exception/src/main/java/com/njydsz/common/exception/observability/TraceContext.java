package com.njydsz.common.exception.observability;

import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.MDC;

/**
 * 分布式追踪上下文（基于 SLF4J MDC）
 *
 * <p>提供 traceId / spanId 的生成、传递和清理能力。
 * 与 Micrometer、Logback、Spring Boot Actuator 无缝集成。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // Web 过滤器入口
 * String traceId = TraceContext.extractOrGenerate(request.getHeader("X-Trace-Id"));
 * TraceContext.setTraceId(traceId);
 * try {
 *     // 业务逻辑，log 中会自动带上 traceId
 *     log.info("处理请求");
 * } finally {
 *     TraceContext.clear();
 * }
 *
 * // 跨服务调用
 * headers.put("X-Trace-Id", TraceContext.getTraceId());
 * }</pre>
 *
 * <p><b>注意：</b>所有方法均为线程局部状态，线程池场景下需要手动透传。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TraceContext {

    /** 默认 traceId MDC key，与 Spring Boot Sleuth/Micrometer Tracing 保持兼容 */
    public static final String TRACE_ID_KEY = "traceId";

    /** spanId MDC key */
    public static final String SPAN_ID_KEY = "spanId";

    /** HTTP header 标准名称（W3C Trace Context） */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 兼容 OpenTelemetry / Zipkin 的 header */
    public static final String HEADER_B3_TRACE_ID = "X-B3-TraceId";

    private TraceContext() {
    }

    /**
     * 提取或生成 traceId
     *
     * <p>优先从 MDC 读取，若不存在则从给定 header 提取，都没有则生成新 UUID。
     *
     * @param headerValue 外部传入的 traceId header 值（可为 null）
     * @return traceId
     */
    public static String extractOrGenerate(String headerValue) {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        if (headerValue != null && !headerValue.isEmpty()) {
            return sanitize(headerValue);
        }
        return generate();
    }

    /**
     * 生成新的 traceId
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置 traceId
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, sanitize(traceId));
        }
    }

    /**
     * 获取当前线程的 traceId
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 设置 spanId
     */
    public static void setSpanId(String spanId) {
        if (spanId != null && !spanId.isEmpty()) {
            MDC.put(SPAN_ID_KEY, sanitize(spanId));
        }
    }

    /**
     * 获取当前线程的 spanId
     */
    public static String getSpanId() {
        return MDC.get(SPAN_ID_KEY);
    }

    /**
     * 设置完整的 traceId + spanId
     */
    public static void setContext(String traceId, String spanId) {
        setTraceId(traceId);
        setSpanId(spanId);
    }

    /**
     * 清理当前线程的追踪上下文（线程池归还前必须调用）
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
    }

    /**
     * 在指定上下文中执行，自动清理
     *
     * @param traceId traceId
     * @param action  要执行的操作
     * @param <T>     返回类型
     * @return action 的返回值
     */
    public static <T> T withContext(String traceId, Supplier<T> action) {
        String previousTraceId = getTraceId();
        String previousSpanId = getSpanId();
        try {
            setTraceId(traceId);
            return action.get();
        } finally {
            if (previousTraceId != null) {
                MDC.put(TRACE_ID_KEY, previousTraceId);
            } else {
                MDC.remove(TRACE_ID_KEY);
            }
            if (previousSpanId != null) {
                MDC.put(SPAN_ID_KEY, previousSpanId);
            } else {
                MDC.remove(SPAN_ID_KEY);
            }
        }
    }

    /**
     * 清理 traceId 中的特殊字符，避免日志注入
     */
    private static String sanitize(String traceId) {
        if (traceId == null) {
            return null;
        }
        // 保留字母数字 + - _
        return traceId.replaceAll("[^A-Za-z0-9\\-_]", "");
    }
}
