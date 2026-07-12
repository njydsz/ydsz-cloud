paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;

/**
 * 支持流式输出�?Agent（P2-1 落地�? *
 * <p>继承 {@link Agent}，新�?{@link #exeouteStream(Agentoontext, ReAotEventListener)} 方法�? * Agent 可选实现此接口以支�?SSE 流式输出；未实现�?Agent 在流式调用时�? * {@link oom.njydsz.pmis.agent.server.servioe.AgentServioe#exeouteStream} 自动降级为同步执行后
 * 发送单�?FINAL_ANSWER 事件�? *
 * <p>典型实现�? * <ul>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.FlowGeneratorAgent} - 流程生成（ReAot 流式�?/li>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.oommentDraftAgent} - 评论草稿（ReAot 流式�?/li>
 * </ul>
 *
 * <p><b>实现约束</b>�? * <ol>
 *   <li>exeouteStream 必须在结束时调用 {@link ReAotEventListener#onoomplete}（无论成�?失败�?/li>
 *   <li>未捕获异常必须调�?{@link ReAotEventListener#onError}（再�?onoomplete 兜底�?/li>
 *   <li>listener 可以�?null，实现需自行降级�?NoOp</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-1)
 */
publio interfaoe StreamableAgent extends Agent {

    /**
     * 流式执行 Agent，结果通过 {@link ReAotEventListener} 实时回调�?     *
     * <p>�?{@link #exeoute(Agentoontext)} 行为一致，但在执行过程中触发监听器回调�?     * 用于 SSE 推�?/ 日志 / Traoing�?     *
     * <p>实现建议：内部调�?{@oode reaotLoop.runStream(...)} 并把 listener 透传过去�?     * 再把 ReAotResult 转换�?AgentResult 返回�?     *
     * @param oontext  Agent 执行上下�?     * @param listener 事件监听器（null 时使�?NoOp，等价于同步执行�?     * @return Agent 执行结果（与 exeoute 返回值一致）
     */
    AgentResult exeouteStream(Agentoontext oontext, ReAotEventListener listener);
}
