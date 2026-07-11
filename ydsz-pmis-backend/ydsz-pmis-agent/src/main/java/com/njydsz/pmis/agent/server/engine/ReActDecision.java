package com.njydsz.pmis.agent.server.engine.react;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * LLM 在 ReAct 循环中输出的决策（P1-2 落地，P1-7 加固）
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
 * <p><b>P1-7 安全加固</b>：提供 {@link #sanitize()} 方法对 LLM 输出做 schema 级
 * 收敛——限制 thought / action 字段最大长度为 {@value #MAX_FIELD_LENGTH} 字符，
 * 防止超长输出导致 token 滥用或 prompt 膨胀。由 {@link ReActLoop} 在反序列化后调用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
@Data
public class ReActDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** thought / action 字段最大允许长度（字符），P1-7 防 prompt 膨胀 */
    public static final int MAX_FIELD_LENGTH = 2000;

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

    /**
     * 对 LLM 输出字段做安全收敛（P1-7）。
     *
     * <p>将 {@link #thought} 与 {@link #action} 截断到 {@value #MAX_FIELD_LENGTH}
     * 字符以内，防止超长输出造成 token 滥用或 prompt 膨胀。{@code finalAnswer}
     * 不做截断（业务最终答案可能较长）。
     *
     * <p>本方法幂等，可重复调用；返回 {@code this} 便于链式调用。
     *
     * @return 当前对象（已收敛）
     */
    public ReActDecision sanitize() {
        this.thought = truncate(this.thought);
        this.action = truncate(this.action);
        return this;
    }

    /** 将字符串截断到 {@link #MAX_FIELD_LENGTH} 字符，超出部分以 "..." 标识 */
    private static String truncate(String s) {
        if (s == null || s.length() <= MAX_FIELD_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_FIELD_LENGTH) + "...";
    }
}
