package com.njydsz.pmis.message.server.tracing;

import com.njydsz.pmis.common.util.TraceIdUtil;
import org.slf4j.MDC;

/**
 * P1-3: 消息全链路追踪上下文（MDC traceId 自动管理）。
 *
 * <p>实现 {@link AutoCloseable}，配合 try-with-resources 在重试 / 死信 / 回执等异步或回调环节
 * 进入时将 traceId 写入 MDC，退出时自动恢复 / 清除，确保日志始终携带 traceId。
 *
 * <p>典型用法：
 * <pre>{@code
 * try (MessageTraceContext ctx = MessageTraceContext.enter(logDO.getTraceId())) {
 *     // 此作用域内 MDC.traceId 已设置，所有日志自动携带
 *     channelRouter.dispatch(logDO);
 * }
 * // 退出后 MDC 自动恢复/清除
 * }</pre>
 *
 * <p>traceId 为 null / 空白时自动生成新 traceId（{@link TraceIdUtil#getOrCreate()}），
 * 保证下游日志可追溯。
 *
 * <p>注意：本类仅管理 MDC 中的 traceId，不干预 Brave / Micrometer Tracing 的 span 上下文。
 * previousTraceId 读取自 {@link MDC#get} 而非 {@link TraceIdUtil#get()}，避免 Brave
 * fallback traceId 干扰恢复逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class MessageTraceContext implements AutoCloseable {

    /** 进入前 MDC 中的 traceId（用于退出时恢复，null 表示原来无） */
    private final String previousTraceId;

    private MessageTraceContext(String previousTraceId) {
        this.previousTraceId = previousTraceId;
    }

    /**
     * 进入追踪上下文：将 traceId 写入 MDC。
     *
     * @param traceId 待设置的 traceId；为 null / 空白时自动生成
     * @return 上下文实例（try-with-resources 自动清理）
     */
    public static MessageTraceContext enter(String traceId) {
        // 仅读取 MDC（不含 Brave fallback），避免恢复时把 Brave traceId 当作 previous
        String previous = MDC.get(TraceIdUtil.TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            TraceIdUtil.getOrCreate();
        } else {
            TraceIdUtil.set(traceId);
        }
        return new MessageTraceContext(previous);
    }

    /**
     * 退出追踪上下文：恢复原 traceId 或清除 MDC。
     */
    @Override
    public void close() {
        if (previousTraceId != null && !previousTraceId.isEmpty()) {
            TraceIdUtil.set(previousTraceId);
        } else {
            TraceIdUtil.clear();
        }
    }
}
