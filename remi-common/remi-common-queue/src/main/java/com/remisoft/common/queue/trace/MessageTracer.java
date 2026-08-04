package com.remisoft.common.queue.trace;

import org.slf4j.MDC;

import com.remisoft.common.core.context.RequestContext;

/**
 * 消息链路追踪工具类
 *
 * <p>基于 SLF4J MDC 和 RequestContext 实现 traceId 的注入、提取和清理，
 * 用于在消息生产/消费全链路中传递追踪标识，便于问题排查和日志关联。
 *
 * <p>集成说明：
 * <ul>
 *   <li>MDC: 用于日志框架自动注入 traceId 到日志输出</li>
 *   <li>RequestContext: 用于跨线程上下文传递，支持线程池场景</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>{@code
 * // 生产端：注入 traceId
 * MessageTracer.injectTraceId(traceId);
 *
 * // 消费端：提取 traceId
 * String traceId = MessageTracer.extractTraceId();
 *
 * // 消息处理完成后清理
 * MessageTracer.clearTraceId();
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class MessageTracer {

    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 注入 traceId 到当前线程的 MDC 和 RequestContext 上下文中
     *
     * @param traceId 链路追踪ID，为 null 或空字符串时忽略
     */
    public static void injectTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, traceId);
            RequestContext.setTraceId(traceId);
        }
    }

    /**
     * 从当前线程的上下文中提取 traceId
     *
     * <p>优先从 RequestContext 获取，其次从 MDC 获取
     *
     * @return 链路追踪ID，未设置时返回 null
     */
    public static String extractTraceId() {
        // 优先从 RequestContext 获取（支持跨线程传递）
        String traceId = RequestContext.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        // 回退到 MDC
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 清除当前线程上下文中的 traceId
     */
    public static void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
        // 注意：不清理 RequestContext 中的 traceId，因为它可能由上层管理
    }
}
