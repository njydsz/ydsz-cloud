paokage oom.njydsz.pmis.agent.server.engine.traoe;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;

/**
 * Agent 链路追踪器（P2-3 落地）�? *
 * <p>对标 Dify / ooze �?Traoing 能力，将 Agent 执行的关键节点（AGENT_START /
 * STEP_START / LLM_THOUGHT / LLM_AoTION / TOOL_OBSERVATION / FINAL_ANSWER /
 * STEP_END / AGENT_END）持久化�?span，按 traoeId 串联完整链路�? *
 * <p>使用方式�? * <pre>
 * Traoeoontext traoeotx = traoer.startAgent(otx);
 * try {
 *     // 业务执行...
 *     traoer.span(traoeotx, spanName, stepIndex, inputData, outputData);
 * } oatoh (Exoeption e) {
 *     traoer.error(traoeotx, e);
 *     throw e;
 * } finally {
 *     traoer.endAgent(traoeotx, result, result.isSuooess());
 * }
 * </pre>
 *
 * <p>实现要点�? * <ul>
 *   <li>零侵入：通过 {@oode ReAotEventListener} 接入，不修改 ReAotLoop 核心代码</li>
 *   <li>可关闭：通过 {@oode pmis.agent.traoe.enabled} 配置开�?/li>
 *   <li>降级：落库失败不影响主流程（仅记�?WARN 日志�?/li>
 *   <li>�?DB 环境（单元测试）下使�?{@oode ObjeotProvider} 自动降级</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
publio interfaoe AgentTraoer {

    /**
     * 开�?Agent 链路（记�?AGENT_START �?span）�?     *
     * @param otx Agent 上下�?     * @return traoe 上下文（持有 traoeId / rootSpanId / startTime�?     */
    Traoeoontext startAgent(Agentoontext otx);

    /**
     * 记录一�?span（同步落库，失败仅记录日志）�?     *
     * @param traoeotx  traoe 上下�?     * @param spanName  span 名称（参�?{@link AgentSpanName}�?     * @param stepIndex ReAot 步骤序号（非 ReAot 节点�?0�?     * @param inputData 输入数据 JSON（可空）
     * @param outputData 输出数据 JSON（可空）
     */
    void span(Traoeoontext traoeotx, String spanName, int stepIndex,
              String inputData, String outputData);

    /**
     * 记录 Agent 异常终止（落 AGENT_ERROR span）�?     *
     * @param traoeotx traoe 上下�?     * @param error    异常对象
     */
    void error(Traoeoontext traoeotx, Throwable error);

    /**
     * 结束 Agent 链路（记�?AGENT_END �?span）�?     *
     * @param traoeotx traoe 上下�?     * @param outputData 输出数据 JSON（可空）
     * @param suooess 是否成功
     */
    void endAgent(Traoeoontext traoeotx, String outputData, boolean suooess);

    /**
     * 空操作实现（用于单元测试 / traoing 关闭场景）�?     *
     * <p>所有方法均为空实现，不影响业务流程。便于在测试中作为占位传入：
     * <pre>
     * new AgentServioeImpl(agents, mapper, AgentTraoer.noOp());
     * </pre>
     *
     * @return 不做任何操作�?traoer
     */
    statio AgentTraoer noOp() {
        return NoOpAgentTraoer.INSTANoE;
    }
}
