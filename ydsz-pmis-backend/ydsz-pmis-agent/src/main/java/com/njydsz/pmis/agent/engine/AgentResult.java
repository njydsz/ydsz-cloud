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
    private AgentType agentType;
    private AgentAlertLevel alertLevel;
    private BigDecimal score;
    private BigDecimal confidence;
    private String suggestion;
    private List<String> matchedRules;
    private Map<String, Object> payload;
}
