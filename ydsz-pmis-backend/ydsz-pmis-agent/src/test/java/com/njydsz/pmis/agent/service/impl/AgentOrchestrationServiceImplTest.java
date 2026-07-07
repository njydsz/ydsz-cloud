package com.njydsz.pmis.agent.service.impl;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.orchestration.AgentCoordinator;
import com.njydsz.pmis.agent.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentOrchestrationServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgentOrchestrationServiceImpl 单元测试")
class AgentOrchestrationServiceImplTest {

    @Mock
    private Agent riskWarningAgent;

    @Mock
    private Agent profitForecastAgent;

    @Mock
    private AgentCoordinator coordinator;

    private AgentOrchestrationServiceImpl orchestrationService;

    @BeforeEach
    void setUp() {
        when(riskWarningAgent.type()).thenReturn(AgentType.RISK_WARNING);
        when(profitForecastAgent.type()).thenReturn(AgentType.PROFIT_FORECAST);

        orchestrationService = new AgentOrchestrationServiceImpl(
                List.of(riskWarningAgent, profitForecastAgent), coordinator);
    }

    // ==================== orchestrate ====================

    @Test
    @DisplayName("orchestrate - 请求为 null 时抛出 BizException")
    void orchestrate_shouldThrowBizExceptionWhenRequestIsNull() {
        BizException ex = assertThrows(BizException.class,
                () -> orchestrationService.orchestrate(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("orchestrate - 正常编排并返回结果")
    void orchestrate_shouldReturnOrchestrationResult() {
        OrchestrationRequest req = buildRequest(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        OrchestrationResult expectedResult = buildOrchestrationResult();
        when(coordinator.coordinate(any(OrchestrationRequest.class), anyMap())).thenReturn(expectedResult);

        OrchestrationResult result = orchestrationService.orchestrate(req);

        assertNotNull(result);
        assertEquals(OrchestrationMode.PARALLEL, result.getMode());
        assertEquals(2, result.getAgentCount());
        verify(coordinator).coordinate(any(OrchestrationRequest.class), anyMap());
    }

    @Test
    @DisplayName("orchestrate - 请求的 Agent 类型无匹配时抛出 BizException")
    void orchestrate_shouldThrowBizExceptionWhenNoAgentMatched() {
        OrchestrationRequest req = buildRequest(List.of("WIN_RATE_PREDICT", "TIMESHEET_ANOMALY"));

        BizException ex = assertThrows(BizException.class,
                () -> orchestrationService.orchestrate(req));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("orchestrate - 部分 Agent 类型未注册时跳过并继续编排")
    void orchestrate_shouldSkipUnregisteredAgentTypes() {
        OrchestrationRequest req = buildRequest(List.of("RISK_WARNING", "UNREGISTERED_TYPE"));
        OrchestrationResult expectedResult = buildOrchestrationResult();
        expectedResult.setAgentCount(1);
        when(coordinator.coordinate(any(OrchestrationRequest.class), anyMap())).thenReturn(expectedResult);

        OrchestrationResult result = orchestrationService.orchestrate(req);

        assertNotNull(result);
        assertEquals(1, result.getAgentCount());
    }

    @Test
    @DisplayName("orchestrate - 空 agentTypes 列表时抛出 BizException")
    void orchestrate_shouldThrowBizExceptionWhenAgentTypesEmpty() {
        OrchestrationRequest req = buildRequest(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> orchestrationService.orchestrate(req));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== agentRegistry ====================

    @Test
    @DisplayName("agentRegistry - 返回已注册 Agent 映射表")
    void agentRegistry_shouldReturnAgentMap() {
        Map<String, Agent> registry = orchestrationService.agentRegistry();

        assertNotNull(registry);
        assertEquals(2, registry.size());
        assertTrue(registry.containsKey("RISK_WARNING"));
        assertTrue(registry.containsKey("PROFIT_FORECAST"));
    }

    @Test
    @DisplayName("agentRegistry - 无 Agent 注册时返回空 Map")
    void agentRegistry_shouldReturnEmptyMapWhenNoAgents() {
        AgentOrchestrationServiceImpl emptyService = new AgentOrchestrationServiceImpl(List.of(), coordinator);

        Map<String, Agent> registry = emptyService.agentRegistry();

        assertNotNull(registry);
        assertTrue(registry.isEmpty());
    }

    // ==================== helper ====================

    private OrchestrationRequest buildRequest(List<String> agentTypes) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId("100");
        req.setBizRef("TEST-REF");
        req.setCallerId("1");
        req.setCallerName("测试用户");
        req.setSource("MANUAL");
        req.setMode(OrchestrationMode.PARALLEL);
        req.setAgentTypes(agentTypes);
        req.setFacts(Map.of("key", "value"));
        return req;
    }

    private OrchestrationResult buildOrchestrationResult() {
        OrchestrationResult result = new OrchestrationResult();
        result.setMode(OrchestrationMode.PARALLEL);
        result.setAgentCount(2);

        AgentResult agentResult = new AgentResult();
        agentResult.setAgentType(AgentType.RISK_WARNING);
        agentResult.setAlertLevel(AgentAlertLevel.NORMAL);
        agentResult.setScore(new BigDecimal("80.0"));
        agentResult.setConfidence(new BigDecimal("0.9"));
        result.setFinalResult(agentResult);
        return result;
    }
}