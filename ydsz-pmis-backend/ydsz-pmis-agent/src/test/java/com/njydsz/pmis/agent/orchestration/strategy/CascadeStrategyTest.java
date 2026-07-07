package com.njydsz.pmis.agent.orchestration.strategy;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.orchestration.AgentBlackboard;
import com.njydsz.pmis.agent.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 级联编排策略单元测试
 *
 * <p>覆盖：空 agentTypes / 第 1 个 Agent 置信度达标提前终止 / 全部不达标取最后一个 /
 * 默认阈值 0.85 / 自定义阈值 / Agent 异常时继续级联。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CascadeStrategy 级联编排策略测试")
class CascadeStrategyTest {

    private CascadeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CascadeStrategy();
    }

    // ==================== 辅助方法 ====================

    private Agent mockAgent(AgentResult result) {
        Agent agent = mock(Agent.class);
        when(agent.execute(any(AgentContext.class))).thenReturn(result);
        return agent;
    }

    private Agent throwingAgent(String msg) {
        Agent agent = mock(Agent.class);
        when(agent.execute(any(AgentContext.class))).thenThrow(new RuntimeException(msg));
        return agent;
    }

    private AgentResult result(double score, double confidence, AgentAlertLevel level) {
        return new AgentResult(AgentType.RISK_WARNING, level,
                BigDecimal.valueOf(score), BigDecimal.valueOf(confidence),
                "建议", null, null);
    }

    private OrchestrationRequest req(List<String> agentTypes) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(OrchestrationMode.CASCADE);
        req.setBizType("project");
        req.setBizId("P001");
        req.setBizRef("PRJ-001");
        req.setCallerId("U001");
        req.setCallerName("张三");
        req.setSource("unit-test");
        req.setAgentTypes(agentTypes);
        req.setFacts(new HashMap<>());
        return req;
    }

    // ==================== 基础属性测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("mode() 返回 CASCADE")
        void shouldReturnCascadeMode() {
            assertThat(strategy.mode()).isEqualTo(OrchestrationMode.CASCADE);
        }
    }

    // ==================== 空入参测试 ====================

    @Nested
    @DisplayName("空入参测试")
    class EmptyInputTest {

        @Test
        @DisplayName("agentTypes=null - 返回空结果")
        void shouldReturnEmptyWhenAgentTypesNull() {
            OrchestrationRequest req = req(null);
            OrchestrationResult r = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(r.getAgentResults()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
            assertThat(r.getNote()).isEqualTo("未指定参与编排的 Agent");
            assertThat(r.getMode()).isEqualTo(OrchestrationMode.CASCADE);
        }

        @Test
        @DisplayName("agentTypes=空列表 - 返回空结果")
        void shouldReturnEmptyWhenAgentTypesEmpty() {
            OrchestrationRequest req = req(Collections.emptyList());
            OrchestrationResult r = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(r.getFinalResult()).isNull();
            assertThat(r.getNote()).isEqualTo("未指定参与编排的 Agent");
        }
    }

    // ==================== 级联执行测试 ====================

    @Nested
    @DisplayName("级联执行测试")
    class CascadeExecutionTest {

        @Test
        @DisplayName("第 1 个 Agent 置信度达标 - 提前终止")
        void shouldTerminateEarlyWhenFirstAgentConfidenceSufficient() {
            OrchestrationRequest req = req(Arrays.asList("A", "B", "C"));
            // 默认阈值 0.85，A 的 confidence=0.9 达标
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.9, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.7, 0.95, AgentAlertLevel.YELLOW)));
            agents.put("C", mockAgent(result(0.9, 0.99, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            // 只执行了 A，提前终止
            assertThat(r.getExecutedAgents()).containsExactly("A");
            assertThat(r.getAgentResults()).containsOnlyKeys("A");
            assertThat(r.getAgentCount()).isEqualTo(1);
            assertThat(r.getFinalResult()).isNotNull();
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.5));
            assertThat(r.getNote()).contains("提前终止");
            assertThat(r.getNote()).contains("A");
        }

        @Test
        @DisplayName("第 2 个 Agent 达标 - 提前终止")
        void shouldTerminateAtSecondAgent() {
            OrchestrationRequest req = req(Arrays.asList("A", "B", "C"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.5, AgentAlertLevel.NORMAL)));   // 未达标
            agents.put("B", mockAgent(result(0.7, 0.9, AgentAlertLevel.YELLOW)));   // 达标
            agents.put("C", mockAgent(result(0.9, 0.99, AgentAlertLevel.RED)));   // 不应执行

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("A", "B");
            assertThat(r.getAgentCount()).isEqualTo(2);
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.7));
            assertThat(r.getNote()).contains("第 2 个");
        }

        @Test
        @DisplayName("全部不达标 - 取最后一个")
        void shouldTakeLastWhenNoneReached() {
            OrchestrationRequest req = req(Arrays.asList("A", "B", "C"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.5, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.7, 0.6, AgentAlertLevel.YELLOW)));
            agents.put("C", mockAgent(result(0.9, 0.7, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("A", "B", "C");
            assertThat(r.getAgentCount()).isEqualTo(3);
            // finalResult 是最后一个（C）
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
            assertThat(r.getNote()).contains("仍不达标");
            assertThat(r.getNote()).contains("C");
        }

        @Test
        @DisplayName("默认阈值 0.85 - confidence=0.85 视为达标")
        void shouldUseDefaultThreshold085() {
            OrchestrationRequest req = req(List.of("A", "B"));
            Map<String, Agent> agents = new HashMap<>();
            // confidence=0.85 等于阈值，应达标
            agents.put("A", mockAgent(result(0.5, 0.85, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.9, 0.99, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("A");
            assertThat(r.getNote()).contains("提前终止");
        }

        @Test
        @DisplayName("默认阈值 0.85 - confidence=0.84 视为未达标")
        void shouldNotReachWhenConfidence084() {
            OrchestrationRequest req = req(List.of("A", "B"));
            Map<String, Agent> agents = new HashMap<>();
            // 两个 Agent 的 confidence=0.84 小于阈值 0.85，均未达标
            agents.put("A", mockAgent(result(0.5, 0.84, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.9, 0.84, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("A", "B");
            assertThat(r.getNote()).contains("仍不达标");
        }

        @Test
        @DisplayName("自定义阈值 0.5 - confidence=0.6 达标")
        void shouldUseCustomThreshold() {
            OrchestrationRequest req = req(List.of("A", "B"));
            req.setConfidenceThreshold(0.5);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.6, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.9, 0.99, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("A");
            assertThat(r.getNote()).contains("提前终止");
        }

        @Test
        @DisplayName("Agent 异常时继续级联 - 不影响后续 Agent")
        void shouldContinueCascadeWhenAgentFails() {
            OrchestrationRequest req = req(Arrays.asList("BAD", "B"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("BAD", throwingAgent("级联异常"));
            agents.put("B", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            // BAD 异常被跳过，B 正常执行
            assertThat(r.getExecutedAgents()).containsExactly("B");
            assertThat(r.getAgentResults()).containsOnlyKeys("B");
            assertThat(r.getFinalResult()).isNotNull();
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
        }

        @Test
        @DisplayName("所有 Agent 都异常 - finalResult=null")
        void shouldReturnNullFinalResultWhenAllAgentsFail() {
            OrchestrationRequest req = req(Arrays.asList("BAD1", "BAD2"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("BAD1", throwingAgent("异常1"));
            agents.put("BAD2", throwingAgent("异常2"));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
            assertThat(r.getNote()).contains("无");
        }

        @Test
        @DisplayName("跳过未注册的 Agent")
        void shouldSkipUnregisteredAgents() {
            OrchestrationRequest req = req(Arrays.asList("MISSING", "B"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("B", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("B");
            assertThat(r.getFinalResult()).isNotNull();
        }

        @Test
        @DisplayName("confidence=null 视为 0 - 永不达标")
        void shouldTreatNullConfidenceAsZero() {
            OrchestrationRequest req = req(List.of("A", "B"));
            Map<String, Agent> agents = new HashMap<>();
            AgentResult ar = new AgentResult(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL,
                    BigDecimal.valueOf(0.5), null, "建议", null, null);
            agents.put("A", mockAgent(ar));
            agents.put("B", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            // A 的 confidence=null → 0，未达标，继续级联到 B
            assertThat(r.getExecutedAgents()).containsExactly("A", "B");
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
        }

        @Test
        @DisplayName("上游 Agent 输出注入下游上下文")
        void shouldInjectUpstreamOutputToDownstreamContext() {
            OrchestrationRequest req = req(List.of("A", "B"));
            Map<String, Agent> agents = new HashMap<>();
            AgentResult upstream = result(0.5, 0.5, AgentAlertLevel.NORMAL);
            agents.put("A", mockAgent(upstream));
            Agent bAgent = mock(Agent.class);
            when(bAgent.execute(any(AgentContext.class))).thenAnswer(inv -> {
                AgentContext ctx = inv.getArgument(0);
                assertThat(ctx.getParams().get("upstream.A")).isSameAs(upstream);
                return result(0.9, 0.95, AgentAlertLevel.RED);
            });
            agents.put("B", bAgent);

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));
            assertThat(r.getFinalResult()).isNotNull();
        }
    }
}
