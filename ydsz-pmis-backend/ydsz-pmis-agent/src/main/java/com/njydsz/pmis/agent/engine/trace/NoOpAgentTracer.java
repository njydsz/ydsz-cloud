package com.njydsz.pmis.agent.engine.trace;

import com.njydsz.pmis.agent.engine.AgentContext;

/**
 * 空操作 Tracer 单例（P2-3 落地）。
 *
 * <p>所有方法均为空实现，用于：
 * <ul>
 *   <li>单元测试中作为占位传入，避免 mock 复杂度</li>
 *   <li>{@code pmis.agent.trace.enabled=false} 时作为生产降级实现</li>
 * </ul>
 *
 * <p>使用 {@link AgentTracer#noOp()} 获取单例实例。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
public final class NoOpAgentTracer implements AgentTracer {

    /** 单例实例 */
    static final NoOpAgentTracer INSTANCE = new NoOpAgentTracer();

    private NoOpAgentTracer() {}

    @Override
    public TraceContext startAgent(AgentContext ctx) {
        // 返回一个最小可用的 TraceContext，避免业务层 NPE
        return TraceContext.builder()
                .traceId(ctx.getTraceId())
                .rootSpanId("noop")
                .agentType(ctx.getBizType())
                .bizType(ctx.getBizType())
                .bizId(ctx.getBizId())
                .bizRef(ctx.getBizRef())
                .providerTraceId(ctx.getProviderTraceId())
                .tenantId("1")
                .startMs(System.currentTimeMillis())
                .stepStartMs(System.currentTimeMillis())
                .build();
    }

    @Override
    public void span(TraceContext traceCtx, String spanName, int stepIndex,
                     String inputData, String outputData) {
        // 空操作
    }

    @Override
    public void error(TraceContext traceCtx, Throwable error) {
        // 空操作
    }

    @Override
    public void endAgent(TraceContext traceCtx, String outputData, boolean success) {
        // 空操作
    }
}
