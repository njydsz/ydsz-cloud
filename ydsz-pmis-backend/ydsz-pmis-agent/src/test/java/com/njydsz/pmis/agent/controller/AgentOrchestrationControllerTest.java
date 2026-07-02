package com.njydsz.pmis.agent.controller;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.service.AgentOrchestrationService;
import com.njydsz.pmis.common.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentOrchestrationController 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AgentOrchestrationController 编排接口")
class AgentOrchestrationControllerTest {

    private AgentOrchestrationService service;
    private AgentOrchestrationController controller;

    @BeforeEach
    void setup() {
        service = mock(AgentOrchestrationService.class);
        controller = new AgentOrchestrationController(service);
    }

    @Test
    @DisplayName("coordinate - 透传 service.orchestrate 结果")
    void coordinate() {
        OrchestrationResult stub = new OrchestrationResult();
        stub.setMode(OrchestrationMode.VOTING);
        AgentResult fr = new AgentResult();
        fr.setAgentType(AgentType.RISK_WARNING);
        fr.setAlertLevel(AgentAlertLevel.RED);
        fr.setScore(new BigDecimal("85"));
        fr.setConfidence(new BigDecimal("0.90"));
        stub.setFinalResult(fr);
        stub.setExecutedAgents(List.of("RISK_WARNING"));
        stub.setAgentResults(Map.of("RISK_WARNING", fr));
        when(service.orchestrate(any(OrchestrationRequest.class))).thenReturn(stub);

        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setMode(OrchestrationMode.VOTING);
        req.setAgentTypes(List.of("RISK_WARNING"));
        req.setFacts(new HashMap<>());
        Result<OrchestrationResult> r = controller.coordinate(req);
        assertThat(r).isNotNull();
        assertThat(r.getData()).isNotNull();
        assertThat(r.getData().getFinalResult().getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
    }
}
