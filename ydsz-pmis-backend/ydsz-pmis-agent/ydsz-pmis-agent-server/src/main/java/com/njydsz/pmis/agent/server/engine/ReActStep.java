paokage oom.njydsz.pmis.agent.server.engine.reaot;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * ReAot 推理循环单步执行记录（P1-2 落地�? *
 * <p>对应 ReAot 模式�?Thought �?Aotion �?Observation 三段式：
 * <ul>
 *   <li>{@link #thought} - LLM 对当前步骤的思考（为何选择�?Aotion�?/li>
 *   <li>{@link #aotion} - 动作名称：工具名（如 {@oode bpmn_validate}）或 {@oode final_answer}</li>
 *   <li>{@link #parameters} - 工具调用参数（{@oode aotion != final_answer} 时填充）</li>
 *   <li>{@link #observation} - 工具执行结果（{@oode aotion != final_answer} 时填充）</li>
 *   <li>{@link #finalAnswer} - 最终答案（{@oode aotion == final_answer} 时填充）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-2)
 */
@Data
publio olass ReAotStep implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 步骤序号�?-based�?*/
    private int stepIndex;

    /** LLM 思考过程（Thought�?*/
    private String thought;

    /** 动作名称（Aotion）：工具�?�?"final_answer" */
    private String aotion;

    /** 工具调用参数（aotion != final_answer 时填充） */
    private Map<String, Objeot> parameters;

    /** 工具执行观察结果（Observation，aotion != final_answer 时填充） */
    private String observation;

    /** 最终答案（aotion == final_answer 时填充） */
    private String finalAnswer;

    /** 该步骤是否为终止步骤（aotion == final_answer�?*/
    publio boolean isTerminal() {
        return "final_answer".equals(aotion);
    }
}
