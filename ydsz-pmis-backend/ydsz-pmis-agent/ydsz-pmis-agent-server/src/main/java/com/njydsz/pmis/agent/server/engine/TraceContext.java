package com.njydsz.pmis.agent.server.engine.trace;

import lombok.Builder;
import lombok.Data;

/**
 * Trace 上下文（P2-3 落地）。
 *
 * <p>持有当前 Agent 执行的链路信息，由 {@link AgentTracer#startAgent} 创建，
 * 在整个 Agent 执行生命周期内透传。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@Data
@Builder
public class TraceContext {

    /** 链路 ID（与 AgentContext.traceId 一致） */
    private String traceId;

    /** 根 span ID（AGENT_START 的 spanId） */
    private String rootSpanId;

    /** Agent 类型 */
    private String agentType;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 业务引用 */
    private String bizRef;

    /** 第三方大模型 provider trace ID */
    private String providerTraceId;

    /** 租户 ID */
    private String tenantId;

    /** Agent 开始时间（毫秒） */
    private long startMs;

    /** 当前步骤开始时间（毫秒） */
    private long stepStartMs;

    /**
     * 标记步骤开始（记录当前时间为步骤开始时间）。
     */
    public void markStepStart() {
        this.stepStartMs = System.currentTimeMillis();
    }

    /**
     * 计算自上次 markStepStart 以来的耗时（毫秒）。
     *
     * @return 步骤耗时；stepStartMs 未设置时返回 0
     */
    public long stepCostMs() {
        if (stepStartMs <= 0) {
            return 0;
        }
        return System.currentTimeMillis() - stepStartMs;
    }
}
