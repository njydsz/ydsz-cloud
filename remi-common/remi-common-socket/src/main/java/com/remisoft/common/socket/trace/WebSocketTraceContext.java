package com.remisoft.common.socket.trace;

import java.util.function.Supplier;

import org.slf4j.MDC;
import com.remisoft.common.util.id.IdGenerator;

/**
 * WebSocket 链路追踪辅助工具（P1-1）。
 *
 * <p>提供 traceId 在 WebSocket 推送全链路中的传递能力。
 * 从 MDC 中获取当前 traceId，注入到集群广播消息中，
 * 订阅端收到消息后恢复 MDC 上下文，实现跨节点链路关联。
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class WebSocketTraceContext {

    /** MDC 中 traceId 的键名 */
    public static final String TRACE_ID_KEY = "traceId";

    private WebSocketTraceContext() {
    }

    /**
     * 获取当前 MDC 中的 traceId，不存在时生成新的。
     *
     * @return traceId
     */
    public static String getOrGenerateTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 获取当前 MDC 中的 traceId。
     *
     * @return traceId，不存在时返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 生成新的 traceId。
     *
     * @return 8 字符短 traceId
     */
    public static String generateTraceId() {
        return IdGenerator.nextIdStr().substring(0, 16);
    }

    /**
     * 设置 traceId 到 MDC。
     *
     * @param traceId 链路追踪 ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 从 MDC 中移除 traceId。
     */
    public static void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 在指定 traceId 上下文中执行操作，执行完毕后自动恢复原 traceId。
     *
     * @param traceId  链路追踪 ID
     * @param runnable 要执行的操作
     */
    public static void runWithTrace(String traceId, Runnable runnable) {
        String previousTraceId = MDC.get(TRACE_ID_KEY);
        try {
            setTraceId(traceId);
            runnable.run();
        } finally {
            if (previousTraceId != null) {
                MDC.put(TRACE_ID_KEY, previousTraceId);
            } else {
                clearTraceId();
            }
        }
    }

    /**
     * 在指定 traceId 上下文中执行有返回值的操作。
     *
     * @param traceId  链路追踪 ID
     * @param supplier 要执行的操作
     * @param <T>      返回值类型
     * @return 操作返回值
     */
    public static <T> T runWithTraceResult(String traceId, Supplier<T> supplier) {
        String previousTraceId = MDC.get(TRACE_ID_KEY);
        try {
            setTraceId(traceId);
            return supplier.get();
        } finally {
            if (previousTraceId != null) {
                MDC.put(TRACE_ID_KEY, previousTraceId);
            } else {
                clearTraceId();
            }
        }
    }
}
