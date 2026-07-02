package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Agent 输出结果
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {
    /** Agent 类型 */
    private AgentType agentType;
    /** 告警等级（RED > YELLOW > INFO = NORMAL = RECOMMEND） */
    private AgentAlertLevel alertLevel;
    /** 综合得分（0-1 或 0-100，由具体 Agent 决定） */
    private BigDecimal score;
    /** 置信度（0-1） */
    private BigDecimal confidence;
    /** 建议措施（文本） */
    private String suggestion;
    /** 命中规则列表 */
    private List<String> matchedRules;
    /** 自由载荷（Agent 自定义输出） */
    private Map<String, Object> payload;
}
