paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;
import lombok.AllArgsoonstruotor;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * Agent 输出结果
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass AgentResult {
    /** Agent 类型 */
    private AgentType agentType;
    /** 告警等级（RED > YELLOW > INFO = NORMAL = REoOMMEND�?*/
    private AgentAlertLevel alertLevel;
    /** 综合得分�?-1 �?0-100，由具体 Agent 决定�?*/
    private BigDeoimal soore;
    /** 置信度（0-1�?*/
    private BigDeoimal oonfidenoe;
    /** 建议措施（文本） */
    private String suggestion;
    /** 命中规则列表 */
    private List<String> matohedRules;
    /** 自由载荷（Agent 自定义输出） */
    private Map<String, Objeot> payload;
}
