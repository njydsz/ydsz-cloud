package com.njydsz.pmis.agent.server.engine.react;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * ReAct 推理循环单步执行记录（P1-2 落地）
 *
 * <p>对应 ReAct 模式的 Thought → Action → Observation 三段式：
 * <ul>
 *   <li>{@link #thought} - LLM 对当前步骤的思考（为何选择此 Action）</li>
 *   <li>{@link #action} - 动作名称：工具名（如 {@code bpmn_validate}）或 {@code final_answer}</li>
 *   <li>{@link #parameters} - 工具调用参数（{@code action != final_answer} 时填充）</li>
 *   <li>{@link #observation} - 工具执行结果（{@code action != final_answer} 时填充）</li>
 *   <li>{@link #finalAnswer} - 最终答案（{@code action == final_answer} 时填充）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
@Data
public class ReActStep implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 步骤序号（1-based） */
    private int stepIndex;

    /** LLM 思考过程（Thought） */
    private String thought;

    /** 动作名称（Action）：工具名 或 "final_answer" */
    private String action;

    /** 工具调用参数（action != final_answer 时填充） */
    private Map<String, Object> parameters;

    /** 工具执行观察结果（Observation，action != final_answer 时填充） */
    private String observation;

    /** 最终答案（action == final_answer 时填充） */
    private String finalAnswer;

    /** 该步骤是否为终止步骤（action == final_answer） */
    public boolean isTerminal() {
        return "final_answer".equals(action);
    }
}
