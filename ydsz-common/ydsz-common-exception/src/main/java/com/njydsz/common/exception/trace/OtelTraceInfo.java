package com.njydsz.common.exception.trace;

import org.springframework.lang.Nullable;

/**
 * OpenTelemetry 链路追踪信息。
 *
 * <p>封装从 OpenTelemetry {@code SpanContext} 中提取的 traceId 与 spanId，
 * 用于注入到 ProblemDetail / 响应头 / 错误码响应体中，实现端到端追踪关联。
 *
 * @param traceId 16 字节 hex 编码 trace ID（32 字符）；OTel 未启用或当前无 trace 上下文时为 null
 * @param spanId  8 字节 hex 编码 span ID（16 字符）；同 traceId 一起为 null
 * @param sampled 当前 span 是否被采样（false 时不记录 span，但 traceId 仍可用于关联日志）
 *
 * @author ydsz-team
 * @since 2.4.0
 */
public record OtelTraceInfo(
        @Nullable String traceId,
        @Nullable String spanId,
        boolean sampled
) {
    /**
     * 判断当前 trace 上下文是否有效（traceId 非空且符合 32 字符 hex 格式）。
     *
     * @return true-有效
     */
    public boolean isValid() {
        return traceId != null && traceId.length() == 32 && traceId.matches("[0-9a-f]+");
    }

    /**
     * 空实例（OTel 未启用或当前无有效 span 时返回）。
     */
    public static final OtelTraceInfo EMPTY = new OtelTraceInfo(null, null, false);

    @Override
    public String toString() {
        if (!isValid()) {
            return "OtelTraceInfo{invalid}";
        }
        return String.format("OtelTraceInfo{traceId='%s', spanId='%s', sampled=%s}",
                traceId, spanId, sampled);
    }
}
