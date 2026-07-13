package com.njydsz.pmis.common.util.id;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.MDC;
import com.njydsz.pmis.common.util.string.StringUtils;
import com.njydsz.pmis.common.util.id.RandomUtils;

/**
 * 链路追踪工具类
 *
 * <p>参考 SkyWalking、Zipkin、Sleuth 等链路追踪规范实现。
 * 支持多种 Trace ID 生成策略和上下文管理，是分布式调用链追踪的基础工具。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li>支持 SkyWalking {@code TraceContext} 集成</li>
 *   <li>支持 SLF4J MDC 上下文注入</li>
 *   <li>支持基于 UUID v7 的 Trace ID 生成</li>
 *   <li>支持 Span ID 父子关系管理</li>
 *   <li>支持线程间链路追踪上下文传递</li>
 * </ul>
 *
 * <p><b>线程安全性：</b>所有方法均为静态无状态，线程安全。
 * 实际状态存储于 SLF4J {@link MDC}，由调用方保证清理。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 在拦截器中获取或创建 Trace ID
 * String traceId = TracerUtils.getOrCreateTraceId();
 *
 * // 创建子 Span
 * String spanId = TracerUtils.createNewSpan();
 *
 * // 在异步线程中传递追踪上下文
 * TracerUtils.runWithTrace(traceId, () -> {
 *     // 业务逻辑
 * });
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public final class TracerUtils {

    private static final String TRACE_ID_NAME = "traceId";
    private static final String SPAN_ID_NAME = "spanId";
    private static final String PARENT_SPAN_ID_NAME = "parentSpanId";

    private TracerUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * 获取链路追踪编号。
     * 1. 首先尝试获取 SkyWalking 的 TraceId。
     * 2. 如果不存在，则尝试从 MDC 中获取。
     * 3. 如果都不存在，则返回空字符串。
     *
     * @return 链路追踪编号
     */
    public static String getTraceId() {
        String traceId = TraceContext.traceId();
        if (StringUtils.isNotEmpty(traceId) && !"Ignored_Trace".equalsIgnoreCase(traceId)) {
            return traceId;
        }
        
        traceId = MDC.get(TRACE_ID_NAME);
        return traceId == null ? "" : traceId;
    }

    /**
     * 获取或创建 Trace ID（如果不存在则自动生成）
     *
     * @return Trace ID
     */
    public static String getOrCreateTraceId() {
        String traceId = getTraceId();
        if (StringUtils.isEmpty(traceId)) {
            traceId = generateTraceId();
            setTraceId(traceId);
        }
        return traceId;
    }

    /**
     * 生成新的 Trace ID（基于 UUID v7）
     *
     * @return 新生成的 Trace ID
     */
    public static String generateTraceId() {
        return UUIDUtils.simpleUuidV7();
    }

    /**
     * 将 Trace ID 注入到 MDC
     *
     * @param traceId Trace ID
     */
    public static void setTraceId(String traceId) {
        if (StringUtils.isNotEmpty(traceId)) {
            MDC.put(TRACE_ID_NAME, traceId);
        }
    }

    /**
     * 获取 Span ID
     *
     * @return Span ID
     */
    public static String getSpanId() {
        String spanId = MDC.get(SPAN_ID_NAME);
        return spanId == null ? "" : spanId;
    }

    /**
     * 设置 Span ID
     *
     * @param spanId Span ID
     */
    public static void setSpanId(String spanId) {
        if (StringUtils.isNotEmpty(spanId)) {
            MDC.put(SPAN_ID_NAME, spanId);
        }
    }

    /**
     * 获取 Parent Span ID
     *
     * @return Parent Span ID
     */
    public static String getParentSpanId() {
        String parentSpanId = MDC.get(PARENT_SPAN_ID_NAME);
        return parentSpanId == null ? "" : parentSpanId;
    }

    /**
     * 设置 Parent Span ID
     *
     * @param parentSpanId Parent Span ID
     */
    public static void setParentSpanId(String parentSpanId) {
        if (StringUtils.isNotEmpty(parentSpanId)) {
            MDC.put(PARENT_SPAN_ID_NAME, parentSpanId);
        }
    }

    /**
     * 创建新的 Span
     * <p>
     * 自动将当前 Span ID 设置为 Parent Span ID，
     * 并生成新的 Span ID。
     * </p>
     *
     * @return 新的 Span ID
     */
    public static String createNewSpan() {
        String currentSpanId = getSpanId();
        if (StringUtils.isNotEmpty(currentSpanId)) {
            setParentSpanId(currentSpanId);
        }
        String newSpanId = generateSpanId();
        setSpanId(newSpanId);
        return newSpanId;
    }

    /**
     * 生成新的 Span ID
     *
     * @return Span ID
     */
    public static String generateSpanId() {
        return RandomUtils.generateNumberString(4);
    }

    /**
     * 获取完整的追踪上下文信息
     *
     * @return 追踪上下文信息字符串
     */
    public static String getTraceContext() {
        String traceId = getTraceId();
        String spanId = getSpanId();
        String parentSpanId = getParentSpanId();
        
        StringBuilder sb = new StringBuilder();
        sb.append("traceId=").append(traceId);
        if (StringUtils.isNotEmpty(spanId)) {
            sb.append(", spanId=").append(spanId);
        }
        if (StringUtils.isNotEmpty(parentSpanId)) {
            sb.append(", parentSpanId=").append(parentSpanId);
        }
        return sb.toString();
    }

    /**
     * 清理 MDC 中的 Trace ID
     */
    public static void clear() {
        MDC.remove(TRACE_ID_NAME);
    }

    /**
     * 清理所有追踪上下文（Trace ID、Span ID、Parent Span ID）
     */
    public static void clearAll() {
        MDC.remove(TRACE_ID_NAME);
        MDC.remove(SPAN_ID_NAME);
        MDC.remove(PARENT_SPAN_ID_NAME);
    }

    /**
     * 检查是否存在有效的 Trace ID
     *
     * @return 是否存在有效的 Trace ID
     */
    public static boolean hasValidTraceId() {
        String traceId = getTraceId();
        return StringUtils.isNotEmpty(traceId) && !"Ignored_Trace".equalsIgnoreCase(traceId);
    }

    /**
     * 执行带追踪上下文的可运行对象
     * <p>
     * 自动设置和清理追踪上下文。
     * </p>
     *
     * @param traceId Trace ID
     * @param runnable 要执行的可运行对象
     */
    public static void runWithTrace(String traceId, Runnable runnable) {
        String originalTraceId = getTraceId();
        try {
            setTraceId(traceId);
            runnable.run();
        } finally {
            if (StringUtils.isNotEmpty(originalTraceId)) {
                setTraceId(originalTraceId);
            } else {
                clear();
            }
        }
    }

    /**
     * 执行带追踪上下文的可运行对象（自动生成 Trace ID）
     *
     * @param runnable 要执行的可运行对象
     */
    public static void runWithTrace(Runnable runnable) {
        String traceId = generateTraceId();
        runWithTrace(traceId, runnable);
    }
}
