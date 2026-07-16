package com.njydsz.common.util;

import com.njydsz.common.util.id.TracerUtils;

/**
 * TraceId 工具类（已废弃，请使用 {@link TracerUtils}）。
 *
 * <p>TracerUtils 提供更完整的链路追踪能力，包括 SkyWalking 集成、Span 管理等。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 请使用 {@link TracerUtils}
 */
@Deprecated(since = "1.4.0", forRemoval = true)
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
     * @deprecated 请使用 {@link TracerUtils#getTraceId()}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String get() {
        String traceId = TracerUtils.getTraceId();
        return traceId.isEmpty() ? null : traceId;
    }

    /**
     * 获取当前 MDC 中的 TraceId，若不存在则生成新的并写入 MDC。
     *
     * @return TraceId
     * @deprecated 请使用 {@link TracerUtils#getOrCreateTraceId()}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String getOrCreate() {
        return TracerUtils.getOrCreateTraceId();
    }

    /**
     * 生成新的 TraceId（不写入 MDC）。
     *
     * @return 新的 TraceId
     * @deprecated 请使用 {@link TracerUtils#generateTraceId()}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static String generate() {
        return TracerUtils.generateTraceId();
    }

    /**
     * 设置 TraceId 到 MDC。
     *
     * @param traceId TraceId
     * @deprecated 请使用 {@link TracerUtils#setTraceId(String)}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static void set(String traceId) {
        TracerUtils.setTraceId(traceId);
    }

    /**
     * 清除 MDC 中的 TraceId。
     *
     * @deprecated 请使用 {@link TracerUtils#clear()}
     */
    @Deprecated(since = "1.4.0", forRemoval = true)
    public static void clear() {
        TracerUtils.clear();
    }
}
