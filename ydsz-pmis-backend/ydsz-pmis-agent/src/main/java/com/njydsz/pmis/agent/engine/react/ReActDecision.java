package com.njydsz.pmis.agent.engine.react;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * LLM 在 ReAct 循环中输出的决策（P1-2 落地）
 *
 * <p>由 {@link LlmProvider#chatForJson(String, String, Class, AgentContext)} 反序列化得到，
 * 是 ReAct 单步循环的核心数据结构。
 *
 * <p>JSON 格式约定（LLM 必须严格输出）：
 * <pre>
 * {
 *   "thought": "我需要先校验 BPMN XML 结构是否完整",
 *   "action": "bpmn_validate",
 *   "parameters": { "bpmnXml": "<bpmn:definitions>...</bpmn:definitions>" },
 *   "finalAnswer": null
 * }
 * </pre>
 *
 * <p>或终止步骤：
 * <pre>
 * {
 *   "thought": "BPMN XML 已校验通过，输出最终结果",
 *   "action": "final_answer",
 *   "parameters": null,
 *   "finalAnswer": "<bpmn:definitions>...</bpmn:definitions>"
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
@Data
public class ReActDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** LLM 思考过程（必填） */
    private String thought;

    /** 动作名称：工具名 或 "final_answer"（必填） */
    private String action;

    /** 工具调用参数（action != final_answer 时必填） */
    private Map<String, Object> parameters;

    /** 最终答案（action == final_answer 时必填） */
    private String finalAnswer;

    /** 是否为终止步骤 */
    public boolean isTerminal() {
        return "final_answer".equalsIgnoreCase(action);
    }
}
