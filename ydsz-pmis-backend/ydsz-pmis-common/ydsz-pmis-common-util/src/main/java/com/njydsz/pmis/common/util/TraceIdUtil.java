package com.njydsz.pmis.common.util;

import org.slf4j.MDC;

import com.njydsz.pmis.common.core.trace.TraceIdGenerator;

/**
 * TraceId 工具类（兼容旧 com.njydsz.pmis.common.util.TraceIdUtil）。
 *
 * <p>提供基于 MDC 的 TraceId 获取与创建能力：
 * <ul>
 *   <li>{@link #get()} — 从 MDC 获取当前 TraceId，无则返回 null</li>
 *   <li>{@link #getOrCreate()} — 从 MDC 获取 TraceId，无则生成并写入 MDC</li>
 *   <li>{@link #generate()} — 生成新的 TraceId（委托给 {@link TraceIdGenerator}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class TraceIdUtil {

    /** MDC 中 TraceId 的键名 */
    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取当前 MDC 中的 TraceId。
     *
     * @return TraceId，不存在时返回 null
     */
    public static String get() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 获取当前 MDC 中的 TraceId，若不存在则生成新的并写入 MDC。
     *
     * @return TraceId
     */
    public static String getOrCreate() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generate();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 生成新的 TraceId（不写入 MDC）。
     *
     * @return 新的 TraceId
     */
    public static String generate() {
        return TraceIdGenerator.generate();
    }

    /**
     * 设置 TraceId 到 MDC。
     *
     * @param traceId TraceId
     */
    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 清除 MDC 中的 TraceId。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
