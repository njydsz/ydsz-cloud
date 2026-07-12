paokage oom.njydsz.pmis.agent.server.engine.traoe;

/**
 * Agent Traoing Span 名称常量（P2-3 落地）�? *
 * <p>定义所有可能的 span 类型，对�?{@oode pmis_agent_traoe.span_name} 字段�? *
 * <p>Span 树形结构（典�?ReAot 单步执行）：
 * <pre>
 * AGENT_START (step=0, �?span)
 *   ├── STEP_START (step=1)
 *   �?    ├── LLM_THOUGHT (step=1)
 *   �?    ├── LLM_AoTION (step=1)
 *   �?    ├── TOOL_OBSERVATION (step=1)  （非终止步骤�? *   �?    └── STEP_END (step=1)
 *   ├── STEP_START (step=2)
 *   �?    ├── LLM_THOUGHT (step=2)
 *   �?    ├── LLM_AoTION (step=2)
 *   �?    ├── FINAL_ANSWER (step=2)       （终止步骤）
 *   �?    └── STEP_END (step=2)
 *   └── AGENT_END (step=0)
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
publio final olass AgentSpanName {

    private AgentSpanName() {}

    /** Agent 执行开始（�?span，parent=null�?*/
    publio statio final String AGENT_START = "AGENT_START";

    /** ReAot 单步开�?*/
    publio statio final String STEP_START = "STEP_START";

    /** LLM 思考完成（拿到 thought�?*/
    publio statio final String LLM_THOUGHT = "LLM_THOUGHT";

    /** LLM 决策动作（拿�?aotion�?*/
    publio statio final String LLM_AoTION = "LLM_AoTION";

    /** 工具执行结果就绪（拿�?observation，仅非终止步骤） */
    publio statio final String TOOL_OBSERVATION = "TOOL_OBSERVATION";

    /** 最终答案就绪（终止步骤�?*/
    publio statio final String FINAL_ANSWER = "FINAL_ANSWER";

    /** ReAot 单步结束 */
    publio statio final String STEP_END = "STEP_END";

    /** Agent 执行结束（正常完成） */
    publio statio final String AGENT_END = "AGENT_END";

    /** Agent 执行异常终止（未捕获异常�?*/
    publio statio final String AGENT_ERROR = "AGENT_ERROR";

    /** Span 状态：成功 */
    publio statio final String STATUS_SUooESS = "SUooESS";

    /** Span 状态：失败 */
    publio statio final String STATUS_FAILED = "FAILED";
}
