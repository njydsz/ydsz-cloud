package com.njydsz.pmis.agent.orchestration;

import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrchestrationRequest / OrchestrationResult 模型测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("Orchestration Request/Result 模型")
class OrchestrationRequestResultTest {

    @Test
    @DisplayName("Request 全字段")
    void requestFields() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1001L);
        req.setBizRef("PRJ-001");
        req.setCallerId(7L);
        req.setCallerName("tester");
        req.setSource("MANUAL");
        req.setMode(OrchestrationMode.VOTING);
        req.setAgentTypes(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        req.setFacts(Map.of("cpi", new BigDecimal("0.95")));
        req.setWeights(Map.of("RISK_WARNING", 0.6, "PROFIT_FORECAST", 0.4));
        req.setConfidenceThreshold(0.85);
        req.setRemark("test");

        assertThat(req.getMode()).isEqualTo(OrchestrationMode.VOTING);
        assertThat(req.getAgentTypes()).hasSize(2);
        assertThat(req.getWeights()).containsEntry("RISK_WARNING", 0.6);
        assertThat(req.getConfidenceThreshold()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("Result 字段")
    void resultFields() {
        OrchestrationResult r = new OrchestrationResult();
        r.setMode(OrchestrationMode.SEQUENTIAL);
        r.setAgentCount(2);
        r.setTotalCostMs(15L);
        r.setNote("ok");
        r.setExecutedAgents(List.of("A", "B"));
        AgentResult finalRes = new AgentResult();
        finalRes.setAgentType(AgentType.RISK_WARNING);
        finalRes.setAlertLevel(AgentAlertLevel.NORMAL);
        r.setFinalResult(finalRes);
        r.setAgentResults(Map.of("A", finalRes));

        assertThat(r.getMode()).isEqualTo(OrchestrationMode.SEQUENTIAL);
        assertThat(r.getAgentCount()).isEqualTo(2);
        assertThat(r.getTotalCostMs()).isEqualTo(15L);
        assertThat(r.getFinalResult().getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
        assertThat(r.getExecutedAgents()).containsExactly("A", "B");
    }

    @Test
    @DisplayName("safeLevel - null 时返回 NORMAL")
    void safeLevelNull() {
        assertThat(OrchestrationResult.safeLevel(null)).isEqualTo("NORMAL");
        assertThat(OrchestrationResult.safeLevel(AgentAlertLevel.RED)).isEqualTo("RED");
    }

    @Test
    @DisplayName("safeBd - null 时返回 0.00")
    void safeBdNull() {
        assertThat(OrchestrationResult.safeBd(null)).isEqualTo("0.00");
        assertThat(OrchestrationResult.safeBd(new BigDecimal("80"))).isEqualTo("80.00");
        assertThat(OrchestrationResult.safeBd(new BigDecimal("80.555"))).isEqualTo("80.56");
    }
}
