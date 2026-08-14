package com.njydsz.common.exception.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * OpenTelemetry 链路追踪信息提取器。
 *
 * <p>在运行时通过反射检测 OpenTelemetry API 是否存在于 classpath：
 * <ul>
 *   <li>若存在：调用 {@code Span.current().getSpanContext()} 提取 traceId / spanId</li>
 *   <li>若不存在（未引入 OTel）：返回 {@link OtelTraceInfo#EMPTY}，无副作用</li>
 * </ul>
 *
 * <p>使用反射而非直接依赖 OpenTelemetry API，避免对未引入 OTel 的项目增加
 * 不必要的依赖（遵循"optional dependency"原则）。
 *
 * <p><b>使用方式：</b>在构建 ProblemDetail / 响应头时调用 {@link #currentTraceInfo()}，
 * 有值时将 traceId 注入到 {@code problem.setProperty("traceId", traceId)} 中。
 *
 * @author ydsz-team
 * @since 2.4.0
 */
@Slf4j
public class OtelTraceInfoExtractor {

    /** OTel Span 类全限定名（反射用） */
    private static final String OTEL_SPAN_CLASS = "io.opentelemetry.api.trace.Span";

    /**
     * OTel 是否可用的标记（首次调用后缓存，避免重复 Class.forName）
     */
    @Nullable
    private static volatile Boolean otelAvailable;

    /**
     * 获取当前线程的 OpenTelemetry 链路追踪信息。
     *
     * <p>无论 OTel 是否接入，本方法都不会抛异常——OTel 未接入时返回
     * {@link OtelTraceInfo#EMPTY}，保证主流程零侵入。
     *
     * @return OtelTraceInfo 实例（永不为 null）
     */
    public static OtelTraceInfo currentTraceInfo() {
        if (!isOtelAvailable()) {
            return OtelTraceInfo.EMPTY;
        }
        try {
            // Span span = Span.current();
            Object span = invokeStaticMethod(OTEL_SPAN_CLASS, "current");
            if (span == null) {
                return OtelTraceInfo.EMPTY;
            }
            // SpanContext ctx = span.getSpanContext();
            Object spanContext = invokeMethod(span, "getSpanContext");
            if (spanContext == null) {
                return OtelTraceInfo.EMPTY;
            }
            // String traceId = ctx.getTraceId();
            String traceId = (String) invokeMethod(spanContext, "getTraceId");
            // String spanId = ctx.getSpanId();
            String spanId = (String) invokeMethod(spanContext, "getSpanId");
            // boolean valid = ctx.isValid();
            boolean valid = (Boolean) invokeMethod(spanContext, "isValid");

            if (!valid) {
                return OtelTraceInfo.EMPTY;
            }

            boolean sampled = (Boolean) invokeMethod(spanContext, "isSampled");

            return new OtelTraceInfo(traceId, spanId, sampled);
        } catch (Exception e) {
            // OTel 运行时异常（如 ClassLoader 变化等），降级为不可用
            log.debug("[OtelTraceInfoExtractor] 提取 OTel TraceInfo 失败: {}", e.getMessage());
            return OtelTraceInfo.EMPTY;
        }
    }

    /**
     * 检测 OpenTelemetry API 是否可用。
     *
     * @return true-classpath 中存在 OTel API
     */
    public static boolean isOtelAvailable() {
        Boolean cached = otelAvailable;
        if (cached != null) {
            return cached;
        }
        try {
            Class.forName(OTEL_SPAN_CLASS);
            otelAvailable = Boolean.TRUE;
            return true;
        } catch (ClassNotFoundException e) {
            otelAvailable = Boolean.FALSE;
            return false;
        }
    }

    /**
     * 反射调用指定类的静态方法。
     *
     * @param className  类全限定名
     * @param methodName 方法名
     * @return 方法返回值
     * @throws Exception 反射调用异常
     */
    private static Object invokeStaticMethod(String className, String methodName) throws Exception {
        Class<?> clazz = Class.forName(className);
        return clazz.getMethod(methodName).invoke(null);
    }

    /**
     * 反射调用指定实例的方法（无参）。
     *
     * @param target     目标实例
     * @param methodName 方法名
     * @return 方法返回值
     * @throws Exception 反射调用异常
     */
    private static Object invokeMethod(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }
}
