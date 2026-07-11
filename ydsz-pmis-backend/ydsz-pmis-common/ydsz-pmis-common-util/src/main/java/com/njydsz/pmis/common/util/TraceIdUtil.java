package com.njydsz.pmis.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID 工具类。
 * <p>
 * 基于 SLF4J MDC 实现轻量级 TraceId 管理，
 * 当 Micrometer Tracing (Brave) 可用时，traceId 会由 TracingFilter 自动写入 MDC。
 * 此工具类提供手动设置/获取/生成 TraceId 的能力。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public final class TraceIdUtil {

    /** MDC 中 traceId 的键名 */
    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";

    private TraceIdUtil() {
    }

    /**
     * 获取当前 MDC 中的 traceId（简写方法）。
     *
     * @return traceId，不存在返回 null
     */
    public static String get() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 设置 traceId 到 MDC（简写方法）。
     *
     * @param traceId 链路追踪 ID
     */
    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 获取当前 MDC 中的 traceId。
     *
     * @return traceId，不存在返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 设置 traceId 到 MDC。
     *
     * @param traceId 链路追踪 ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 生成新的 traceId 并设置到 MDC。
     *
     * @return 生成的 traceId
     */
    public static String generateAndSet() {
        String traceId = generate();
        setTraceId(traceId);
        return traceId;
    }

    /**
     * 生成新的 traceId（不设置到 MDC）。
     *
     * @return 32 位 hex 格式的 traceId
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 清除 MDC 中的 traceId。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
    }

    /**
     * 确保有 traceId，没有则生成。
     *
     * @return 当前 traceId
     */
    public static String ensureTraceId() {
        String traceId = getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = generateAndSet();
        }
        return traceId;
    }

    /**
     * 确保有 traceId，没有则生成（简写方法别名）。
     *
     * @return 当前 traceId
     */
    public static String getOrCreate() {
        return ensureTraceId();
    }
}
