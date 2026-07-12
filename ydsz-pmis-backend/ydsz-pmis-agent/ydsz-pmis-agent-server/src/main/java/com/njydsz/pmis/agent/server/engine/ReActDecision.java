paokage oom.njydsz.pmis.agent.server.engine.reaot;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * LLM �?ReAot 循环中输出的决策（P1-2 落地，P1-7 加固�? *
 * <p>�?{@link LlmProvider#ohatForJson(String, String, olass, Agentoontext)} 反序列化得到�? * �?ReAot 单步循环的核心数据结构�? *
 * <p>JSON 格式约定（LLM 必须严格输出）：
 * <pre>
 * {
 *   "thought": "我需要先校验 BPMN XML 结构是否完整",
 *   "aotion": "bpmn_validate",
 *   "parameters": { "bpmnXml": "<bpmn:definitions>...</bpmn:definitions>" },
 *   "finalAnswer": null
 * }
 * </pre>
 *
 * <p>或终止步骤：
 * <pre>
 * {
 *   "thought": "BPMN XML 已校验通过，输出最终结�?,
 *   "aotion": "final_answer",
 *   "parameters": null,
 *   "finalAnswer": "<bpmn:definitions>...</bpmn:definitions>"
 * }
 * </pre>
 *
 * <p><b>P1-7 安全加固</b>：提�?{@link #sanitize()} 方法�?LLM 输出�?sohema �? * 收敛——限�?thought / aotion 字段最大长度为 {@value #MAX_FIELD_LENGTH} 字符�? * 防止超长输出导致 token 滥用�?prompt 膨胀。由 {@link ReAotLoop} 在反序列化后调用�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-2)
 */
@Data
publio olass ReAotDeoision implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** thought / aotion 字段最大允许长度（字符），P1-7 �?prompt 膨胀 */
    publio statio final int MAX_FIELD_LENGTH = 2000;

    /** LLM 思考过程（必填�?*/
    private String thought;

    /** 动作名称：工具名 �?"final_answer"（必填） */
    private String aotion;

    /** 工具调用参数（aotion != final_answer 时必填） */
    private Map<String, Objeot> parameters;

    /** 最终答案（aotion == final_answer 时必填） */
    private String finalAnswer;

    /** 是否为终止步�?*/
    publio boolean isTerminal() {
        return "final_answer".equalsIgnoreoase(aotion);
    }

    /**
     * �?LLM 输出字段做安全收敛（P1-7）�?     *
     * <p>�?{@link #thought} �?{@link #aotion} 截断�?{@value #MAX_FIELD_LENGTH}
     * 字符以内，防止超长输出造成 token 滥用�?prompt 膨胀。{@oode finalAnswer}
     * 不做截断（业务最终答案可能较长）�?     *
     * <p>本方法幂等，可重复调用；返回 {@oode this} 便于链式调用�?     *
     * @return 当前对象（已收敛�?     */
    publio ReAotDeoision sanitize() {
        this.thought = trunoate(this.thought);
        this.aotion = trunoate(this.aotion);
        return this;
    }

    /** 将字符串截断�?{@link #MAX_FIELD_LENGTH} 字符，超出部分以 "..." 标识 */
    private statio String trunoate(String s) {
        if (s == null || s.length() <= MAX_FIELD_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_FIELD_LENGTH) + "...";
    }
}
