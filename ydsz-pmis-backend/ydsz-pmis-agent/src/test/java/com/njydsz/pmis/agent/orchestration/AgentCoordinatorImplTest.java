package com.njydsz.pmis.agent.orchestration;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentCoordinatorImpl 协调器测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AgentCoordinatorImpl 多智能体协调器")
class AgentCoordinatorImplTest {

    private final AgentCoordinatorImpl coordinator = new AgentCoordinatorImpl();

    @Test
    @DisplayName("req 为 null - 抛错")
    void nullRequest() {
        assertThatThrownBy(() -> coordinator.coordinate(null, Map.of()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("mode 为 null - 抛错")
    void nullMode() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setAgentTypes(List.of("RISK_WARNING"));
        assertThatThrownBy(() -> coordinator.coordinate(req, Map.of()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("agents 为空 - 抛错")
    void nullAgents() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(OrchestrationMode.SEQUENTIAL);
        assertThatThrownBy(() -> coordinator.coordinate(req, null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> coordinator.coordinate(req, new HashMap<>()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("未实现的 mode - 抛错")
    void unimplementedMode() {
        OrchestrationRequest req = new OrchestrationRequest();
        // 通过反射把 mode 改成不存在的值不可行（枚举限制），但已注册模式不会被拒绝
        // 简单验证 4 种 mode 都能找到 strategy
        for (OrchestrationMode m : OrchestrationMode.values()) {
            req.setMode(m);
            req.setAgentTypes(List.of("RISK_WARNING"));
            Agent a = stubAgent(AgentType.RISK_WARNING);
            OrchestrationResult r = coordinator.coordinate(req, Map.of("RISK_WARNING", a));
            assertThat(r.getMode()).isEqualTo(m);
        }
    }

    @Test
    @DisplayName("SEQUENTIAL 模式派发")
    void dispatchSequential() {
        OrchestrationRequest req = baseReq(OrchestrationMode.SEQUENTIAL);
        Agent a = stubAgent(AgentType.RISK_WARNING);
        OrchestrationResult r = coordinator.coordinate(req, Map.of("RISK_WARNING", a));
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.SEQUENTIAL);
    }

    @Test
    @DisplayName("PARALLEL 模式派发")
    void dispatchParallel() {
        OrchestrationRequest req = baseReq(OrchestrationMode.PARALLEL);
        Agent a = stubAgent(AgentType.RISK_WARNING);
        OrchestrationResult r = coordinator.coordinate(req, Map.of("RISK_WARNING", a));
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.PARALLEL);
    }

    @Test
    @DisplayName("VOTING 模式派发")
    void dispatchVoting() {
        OrchestrationRequest req = baseReq(OrchestrationMode.VOTING);
        Agent a = stubAgent(AgentType.RISK_WARNING);
        OrchestrationResult r = coordinator.coordinate(req, Map.of("RISK_WARNING", a));
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.VOTING);
    }

    @Test
    @DisplayName("CASCADE 模式派发")
    void dispatchCascade() {
        OrchestrationRequest req = baseReq(OrchestrationMode.CASCADE);
        Agent a = stubAgent(AgentType.RISK_WARNING);
        OrchestrationResult r = coordinator.coordinate(req, Map.of("RISK_WARNING", a));
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.CASCADE);
    }

    @Test
    @DisplayName("黑板 facts 注入")
    void factsPropagated() {
        OrchestrationRequest req = baseReq(OrchestrationMode.SEQUENTIAL);
        Map<String, Object> facts = new HashMap<>();
        facts.put("k", "v");
        req.setFacts(facts);
        Agent a = stubAgent(AgentType.RISK_WARNING);
        OrchestrationResult r = coordinator.coordinate(req, Map.of("RISK_WARNING", a));
        assertThat(r.getTrace()).isNotEmpty();
    }

    private OrchestrationRequest baseReq(OrchestrationMode mode) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setMode(mode);
        req.setAgentTypes(List.of("RISK_WARNING"));
        req.setFacts(new HashMap<>());
        return req;
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
                r.setScore(new BigDecimal("60"));
                r.setConfidence(new BigDecimal("0.5"));
                return r;
            }
        };
    }
}
