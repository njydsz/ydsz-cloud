package com.njydsz.pmis.agent.server.engine.trace;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import lombok.Builder;
import lombok.Data;

/**
 * Agent Span 数据传输对象（P2-3 落地）。
 *
 * <p>表示一个 Tracing 节点，由 {@link AgentTracer} 持久化到 {@code pmis_agent_trace} 表。
 * 一个 Span = 一次 ReAct 事件回调（如 onThought / onAction / onObservation）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@Data
@Builder
public class AgentSpan {

    /** 链路 ID（与 AgentContext.traceId 对齐） */
    private String traceId;

    /** 本 span ID（雪花算法字符串） */
    private String spanId;

    /** 父 span ID（AGENT_START 为根，parent=null） */
    private String parentSpanId;

    /** Agent 类型（RISK_WARNING 等） */
    private String agentType;

    /** 业务类型（PROJECT 等） */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 业务引用 */
    private String bizRef;

    /** Span 名称（参考 {@link AgentSpanName}） */
    private String spanName;

    /** ReAct 步骤序号（1-based；非 ReAct 节点为 0） */
    private int stepIndex;

    /** Span 状态：SUCCESS / FAILED */
    private String status;

    /** 输入数据 JSON */
    private String inputData;

    /** 输出数据 JSON */
    private String outputData;

    /** 错误信息（status=FAILED 时填） */
    private String errorMsg;

    /** 本 span 耗时（毫秒） */
    private long costMs;

    /** 第三方大模型 provider trace ID */
    private String providerTraceId;

    /** 租户 ID */
    private String tenantId;

    /**
     * 从 AgentContext 提取公共字段构造 Span builder（不含 spanName/stepIndex/inputData 等业务字段）。
     *
     * @param ctx Agent 上下文
     * @return 预填好公共字段的 builder
     */
    public static AgentSpanBuilder fromContext(AgentContext ctx) {
        return AgentSpan.builder()
                .traceId(ctx.getTraceId())
                .bizType(ctx.getBizType())
                .bizId(ctx.getBizId())
                .bizRef(ctx.getBizRef())
                .providerTraceId(ctx.getProviderTraceId())
                .tenantId("1");
    }
}
