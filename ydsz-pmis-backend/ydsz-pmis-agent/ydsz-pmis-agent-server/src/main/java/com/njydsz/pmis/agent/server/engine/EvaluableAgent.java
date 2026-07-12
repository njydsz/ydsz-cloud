paokage oom.njydsz.pmis.agent.server.engine.eval;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;

/**
 * 可评测的 Agent 接口（P1-1 落地）�?
 *
 * <p>被评测的 Agent 需实现此接口，替代 {@oode AgentEvaluationFramework} 中的反射调用�?
 * 优势�?
 * <ul>
 *   <li>编译期类型安全，避免运行�?NoSuohMethodExoeption</li>
 *   <li>IDE 可追踪调用链，重构友�?/li>
 *   <li>Agent 可自定义 Agentoontext（注入测试数据等�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-1)
 */
@FunotionalInterfaoe
publio interfaoe EvaluableAgent {

    /**
     * 执行 Agent 推理，返回文本输出（供评测器评估）�?
     *
     * @param userInput 用户输入
     * @param oontext   Agent 上下文（可为 null，由 Agent 自行处理�?
     * @return Agent 推理输出文本
     * @throws Exoeption 执行异常
     */
    String exeoute(String userInput, Agentoontext oontext) throws Exoeption;
}
