package com.njydsz.pmis.agent.server.engine.trace;

/**
 * Agent Tracing Span 名称常量（P2-3 落地）。
 *
 * <p>定义所有可能的 span 类型，对应 {@code pmis_agent_trace.span_name} 字段。
 *
 * <p>Span 树形结构（典型 ReAct 单步执行）：
 * <pre>
 * AGENT_START (step=0, 根 span)
 *   ├── STEP_START (step=1)
 *   │     ├── LLM_THOUGHT (step=1)
 *   │     ├── LLM_ACTION (step=1)
 *   │     ├── TOOL_OBSERVATION (step=1)  （非终止步骤）
 *   │     └── STEP_END (step=1)
 *   ├── STEP_START (step=2)
 *   │     ├── LLM_THOUGHT (step=2)
 *   │     ├── LLM_ACTION (step=2)
 *   │     ├── FINAL_ANSWER (step=2)       （终止步骤）
 *   │     └── STEP_END (step=2)
 *   └── AGENT_END (step=0)
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
public final class AgentSpanName {

    private AgentSpanName() {}

    /** Agent 执行开始（根 span，parent=null） */
    public static final String AGENT_START = "AGENT_START";

    /** ReAct 单步开始 */
    public static final String STEP_START = "STEP_START";

    /** LLM 思考完成（拿到 thought） */
    public static final String LLM_THOUGHT = "LLM_THOUGHT";

    /** LLM 决策动作（拿到 action） */
    public static final String LLM_ACTION = "LLM_ACTION";

    /** 工具执行结果就绪（拿到 observation，仅非终止步骤） */
    public static final String TOOL_OBSERVATION = "TOOL_OBSERVATION";

    /** 最终答案就绪（终止步骤） */
    public static final String FINAL_ANSWER = "FINAL_ANSWER";

    /** ReAct 单步结束 */
    public static final String STEP_END = "STEP_END";

    /** Agent 执行结束（正常完成） */
    public static final String AGENT_END = "AGENT_END";

    /** Agent 执行异常终止（未捕获异常） */
    public static final String AGENT_ERROR = "AGENT_ERROR";

    /** Span 状态：成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** Span 状态：失败 */
    public static final String STATUS_FAILED = "FAILED";
}
