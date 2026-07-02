package com.njydsz.pmis.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID 工具
 *
 * <p>基于 MDC 存储 traceId，配合日志框架输出。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class TraceIdUtil {

    /** MDC 中 traceId 的 key */
    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtil() {
    }

    /**
     * 生成 16 位 traceId
     *
     * @return traceId 字符串
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 获取当前线程的 traceId
     *
     * @return traceId；未设置时返回空字符串
     */
    public static String get() {
        String id = MDC.get(TRACE_ID_KEY);
        return id == null ? "" : id;
    }

    /**
     * 获取或创建：未设置时自动生成 16 位
     *
     * @return 非空 traceId
     */
    public static String getOrCreate() {
        String id = MDC.get(TRACE_ID_KEY);
        if (id == null || id.isEmpty()) {
            id = generate();
            MDC.put(TRACE_ID_KEY, id);
        }
        return id;
    }

    /**
     * 设置 traceId 到当前线程 MDC
     *
     * @param traceId traceId
     */
    public static void set(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /**
     * 清除当前线程的 traceId
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
