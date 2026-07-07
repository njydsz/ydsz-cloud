package com.njydsz.pmis.agent.engine.trace;

import com.njydsz.pmis.agent.engine.AgentContext;

/**
 * Agent 链路追踪器（P2-3 落地）。
 *
 * <p>对标 Dify / Coze 的 Tracing 能力，将 Agent 执行的关键节点（AGENT_START /
 * STEP_START / LLM_THOUGHT / LLM_ACTION / TOOL_OBSERVATION / FINAL_ANSWER /
 * STEP_END / AGENT_END）持久化为 span，按 traceId 串联完整链路。
 *
 * <p>使用方式：
 * <pre>
 * TraceContext traceCtx = tracer.startAgent(ctx);
 * try {
 *     // 业务执行...
 *     tracer.span(traceCtx, spanName, stepIndex, inputData, outputData);
 * } catch (Exception e) {
 *     tracer.error(traceCtx, e);
 *     throw e;
 * } finally {
 *     tracer.endAgent(traceCtx, result, result.isSuccess());
 * }
 * </pre>
 *
 * <p>实现要点：
 * <ul>
 *   <li>零侵入：通过 {@code ReActEventListener} 接入，不修改 ReActLoop 核心代码</li>
 *   <li>可关闭：通过 {@code pmis.agent.trace.enabled} 配置开关</li>
 *   <li>降级：落库失败不影响主流程（仅记录 WARN 日志）</li>
 *   <li>无 DB 环境（单元测试）下使用 {@code ObjectProvider} 自动降级</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
public interface AgentTracer {

    /**
     * 开始 Agent 链路（记录 AGENT_START 根 span）。
     *
     * @param ctx Agent 上下文
     * @return trace 上下文（持有 traceId / rootSpanId / startTime）
     */
    TraceContext startAgent(AgentContext ctx);

    /**
     * 记录一个 span（同步落库，失败仅记录日志）。
     *
     * @param traceCtx  trace 上下文
     * @param spanName  span 名称（参考 {@link AgentSpanName}）
     * @param stepIndex ReAct 步骤序号（非 ReAct 节点为 0）
     * @param inputData 输入数据 JSON（可空）
     * @param outputData 输出数据 JSON（可空）
     */
    void span(TraceContext traceCtx, String spanName, int stepIndex,
              String inputData, String outputData);

    /**
     * 记录 Agent 异常终止（落 AGENT_ERROR span）。
     *
     * @param traceCtx trace 上下文
     * @param error    异常对象
     */
    void error(TraceContext traceCtx, Throwable error);

    /**
     * 结束 Agent 链路（记录 AGENT_END 根 span）。
     *
     * @param traceCtx trace 上下文
     * @param outputData 输出数据 JSON（可空）
     * @param success 是否成功
     */
    void endAgent(TraceContext traceCtx, String outputData, boolean success);

    /**
     * 空操作实现（用于单元测试 / tracing 关闭场景）。
     *
     * <p>所有方法均为空实现，不影响业务流程。便于在测试中作为占位传入：
     * <pre>
     * new AgentServiceImpl(agents, mapper, AgentTracer.noOp());
     * </pre>
     *
     * @return 不做任何操作的 tracer
     */
    static AgentTracer noOp() {
        return NoOpAgentTracer.INSTANCE;
    }
}
