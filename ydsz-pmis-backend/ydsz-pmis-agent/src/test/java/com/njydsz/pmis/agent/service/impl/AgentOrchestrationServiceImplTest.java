package com.njydsz.pmis.agent.service.impl;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.engine.RiskWarningAgent;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.orchestration.AgentCoordinator;
import com.njydsz.pmis.agent.orchestration.AgentCoordinatorImpl;
import com.njydsz.pmis.agent.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentOrchestrationServiceImpl 编排服务测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AgentOrchestrationServiceImpl 多智能体编排服务")
class AgentOrchestrationServiceImplTest {

    private AgentOrchestrationServiceImpl service;

    @BeforeEach
    void setup() {
        AgentCoordinator coordinator = new AgentCoordinatorImpl();
        service = new AgentOrchestrationServiceImpl(
                List.of(new RiskWarningAgent(), stubAgent(AgentType.PROFIT_FORECAST)),
                coordinator);
    }

    @Test
    @DisplayName("agentRegistry - 完整注册表")
    void agentRegistry() {
        Map<String, Agent> reg = service.agentRegistry();
        assertThat(reg).containsKeys("RISK_WARNING", "PROFIT_FORECAST");
        assertThat(reg.get("RISK_WARNING")).isInstanceOf(RiskWarningAgent.class);
    }

    @Test
    @DisplayName("orchestrate - req 为 null")
    void nullRequest() {
        assertThatThrownBy(() -> service.orchestrate(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("orchestrate - agentTypes 为空")
    void emptyAgentTypes() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(OrchestrationMode.SEQUENTIAL);
        req.setAgentTypes(List.of());
        assertThatThrownBy(() -> service.orchestrate(req))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("orchestrate - 所有 agentType 都未注册")
    void allUnregistered() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(OrchestrationMode.SEQUENTIAL);
        req.setAgentTypes(List.of("X", "Y"));
        assertThatThrownBy(() -> service.orchestrate(req))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("orchestrate - 部分 agentType 未注册 - 只过滤已注册")
    void partialRegistered() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(OrchestrationMode.SEQUENTIAL);
        req.setAgentTypes(List.of("RISK_WARNING", "X"));
        OrchestrationResult r = service.orchestrate(req);
        assertThat(r.getExecutedAgents()).contains("RISK_WARNING");
        assertThat(r.getExecutedAgents()).doesNotContain("X");
    }

    @Test
    @DisplayName("orchestrate - 正常执行风险预警")
    void orchestrateRiskWarning() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setMode(OrchestrationMode.SEQUENTIAL);
        req.setAgentTypes(List.of("RISK_WARNING"));
        Map<String, Object> facts = new HashMap<>();
        facts.put("cpi", new BigDecimal("0.70"));
        facts.put("spi", new BigDecimal("0.70"));
        req.setFacts(facts);
        OrchestrationResult r = service.orchestrate(req);
        assertThat(r).isNotNull();
        assertThat(r.getAgentResults()).containsKey("RISK_WARNING");
        assertThat(r.getFinalResult().getAlertLevel()).isIn(AgentAlertLevel.RED, AgentAlertLevel.YELLOW);
    }

    @Test
    @DisplayName("orchestrate - VOTING 模式加权融合")
    void orchestrateVoting() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setMode(OrchestrationMode.VOTING);
        req.setAgentTypes(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        req.setWeights(Map.of("RISK_WARNING", 0.6, "PROFIT_FORECAST", 0.4));
        OrchestrationResult r = service.orchestrate(req);
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.VOTING);
        assertThat(r.getFinalResult()).isNotNull();
    }

    @Test
    @DisplayName("orchestrate - CASCADE 模式 提前终止")
    void orchestrateCascade() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setMode(OrchestrationMode.CASCADE);
        req.setAgentTypes(List.of("RISK_WARNING"));
        req.setConfidenceThreshold(0.5);
        OrchestrationResult r = service.orchestrate(req);
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.CASCADE);
        assertThat(r.getExecutedAgents()).contains("RISK_WARNING");
    }

    @Test
    @DisplayName("orchestrate - PARALLEL 模式")
    void orchestrateParallel() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setMode(OrchestrationMode.PARALLEL);
        req.setAgentTypes(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        OrchestrationResult r = service.orchestrate(req);
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.PARALLEL);
        assertThat(r.getAgentResults()).hasSize(2);
    }

    private Agent stubAgent(AgentType t) {
        return new Agent() {
            @Override
            public AgentType type() { return t; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                AgentResult r = new AgentResult();
                r.setAgentType(t);
                r.setAlertLevel(AgentAlertLevel.NORMAL);
                r.setScore(new BigDecimal("80"));
                r.setConfidence(new BigDecimal("0.85"));
                r.setSuggestion("stub");
                return r;
            }
        };
    }
}
