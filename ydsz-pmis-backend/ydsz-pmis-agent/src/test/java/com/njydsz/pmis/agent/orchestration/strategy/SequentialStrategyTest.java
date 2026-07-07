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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 顺序编排策略单元测试
 *
 * <p>覆盖：空 agentTypes / 跳过未注册 Agent / 正常顺序执行 / 单 Agent 异常隔离 / 最后 Agent 输出为 finalResult。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SequentialStrategy 顺序编排策略测试")
class SequentialStrategyTest {

    private SequentialStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SequentialStrategy();
    }

    // ==================== 辅助方法 ====================

    /** 构造一个返回固定结果的 Agent */
    private Agent mockAgent(AgentResult result) {
        Agent agent = mock(Agent.class);
        when(agent.execute(any(AgentContext.class))).thenReturn(result);
        return agent;
    }

    /** 构造一个抛异常的 Agent */
    private Agent throwingAgent(String msg) {
        Agent agent = mock(Agent.class);
        when(agent.execute(any(AgentContext.class))).thenThrow(new RuntimeException(msg));
        return agent;
    }

    /** 构造 AgentResult */
    private AgentResult result(double score, double confidence, AgentAlertLevel level) {
        return new AgentResult(AgentType.RISK_WARNING, level,
                BigDecimal.valueOf(score), BigDecimal.valueOf(confidence),
                "建议", null, null);
    }

    /** 构造编排请求 */
    private OrchestrationRequest req(List<String> agentTypes) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(OrchestrationMode.SEQUENTIAL);
        req.setBizType("project");
        req.setBizId("P001");
        req.setBizRef("PRJ-001");
        req.setCallerId("U001");
        req.setCallerName("张三");
        req.setSource("unit-test");
        req.setAgentTypes(agentTypes);
        Map<String, Object> facts = new HashMap<>();
        facts.put("k", "v");
        req.setFacts(facts);
        return req;
    }

    // ==================== 基础属性测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("mode() 返回 SEQUENTIAL")
        void shouldReturnSequentialMode() {
            assertThat(strategy.mode()).isEqualTo(OrchestrationMode.SEQUENTIAL);
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
            OrchestrationResult result = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(result.getAgentResults()).isEmpty();
            assertThat(result.getExecutedAgents()).isEmpty();
            assertThat(result.getFinalResult()).isNull();
            assertThat(result.getNote()).isEqualTo("未指定参与编排的 Agent");
            assertThat(result.getMode()).isEqualTo(OrchestrationMode.SEQUENTIAL);
            assertThat(result.getAgentCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("agentTypes=空列表 - 返回空结果")
        void shouldReturnEmptyWhenAgentTypesEmpty() {
            OrchestrationRequest req = req(Collections.emptyList());
            OrchestrationResult result = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(result.getAgentResults()).isEmpty();
            assertThat(result.getExecutedAgents()).isEmpty();
            assertThat(result.getFinalResult()).isNull();
            assertThat(result.getNote()).isEqualTo("未指定参与编排的 Agent");
        }
    }

    // ==================== 执行逻辑测试 ====================

    @Nested
    @DisplayName("执行逻辑测试")
    class ExecutionTest {

        @Test
        @DisplayName("跳过未注册的 Agent - 仅执行已注册的")
        void shouldSkipUnregisteredAgents() {
            OrchestrationRequest req = req(Arrays.asList("A", "MISSING", "B"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.7, 0.9, AgentAlertLevel.YELLOW)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("A", "B");
            assertThat(r.getAgentResults()).containsKeys("A", "B");
            assertThat(r.getAgentCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("正常顺序执行 - 3 个 Agent 按序执行")
        void shouldExecuteSequentially() {
            OrchestrationRequest req = req(Arrays.asList("A", "B", "C"));
            Map<String, Agent> agents = new LinkedHashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.7, 0.85, AgentAlertLevel.YELLOW)));
            agents.put("C", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactly("A", "B", "C");
            assertThat(r.getAgentResults()).hasSize(3);
            assertThat(r.getAgentCount()).isEqualTo(3);
            assertThat(r.getNote()).isEqualTo("顺序执行完成");
            assertThat(r.getMode()).isEqualTo(OrchestrationMode.SEQUENTIAL);
            assertThat(r.getTrace()).hasSize(3);
            assertThat(r.getTotalCostMs()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("单 Agent 异常隔离 - 不影响其他 Agent 执行")
        void shouldIsolateAgentException() {
            OrchestrationRequest req = req(Arrays.asList("A", "BAD", "C"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("BAD", throwingAgent("模拟异常"));
            agents.put("C", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            // BAD 异常被隔离，A 和 C 正常执行
            assertThat(r.getExecutedAgents()).containsExactly("A", "C");
            assertThat(r.getAgentResults()).containsOnlyKeys("A", "C");
            assertThat(r.getAgentCount()).isEqualTo(2);
            // finalResult 是最后一个成功的 Agent 输出（C）
            assertThat(r.getFinalResult()).isNotNull();
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.9));
        }

        @Test
        @DisplayName("finalResult = 最后一个成功 Agent 的输出")
        void shouldSetFinalResultToLastSuccessfulAgent() {
            OrchestrationRequest req = req(Arrays.asList("A", "B"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.95, 0.99, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getFinalResult()).isNotNull();
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.95));
            assertThat(r.getFinalResult().getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
        }

        @Test
        @DisplayName("全部 Agent 都异常 - finalResult 为 null")
        void shouldReturnNullFinalResultWhenAllAgentsFail() {
            OrchestrationRequest req = req(Arrays.asList("BAD1", "BAD2"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("BAD1", throwingAgent("异常1"));
            agents.put("BAD2", throwingAgent("异常2"));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).isEmpty();
            assertThat(r.getAgentResults()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
            assertThat(r.getAgentCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("上游 Agent 输出注入下游上下文 - 通过黑板 scratch")
        void shouldInjectUpstreamOutputToDownstreamContext() {
            OrchestrationRequest req = req(List.of("A", "B"));
            Map<String, Agent> agents = new HashMap<>();
            // 第一个 Agent 返回特定结果，记录在黑板
            AgentResult upstream = result(0.5, 0.8, AgentAlertLevel.NORMAL);
            agents.put("A", mockAgent(upstream));
            // 第二个 Agent 捕获上下文中的 upstream.A
            Agent bAgent = mock(Agent.class);
            when(bAgent.execute(any(AgentContext.class))).thenAnswer(inv -> {
                AgentContext ctx = inv.getArgument(0);
                // 验证 upstream.A 已被注入
                assertThat(ctx.getParams().get("upstream.A")).isSameAs(upstream);
                return result(0.9, 0.95, AgentAlertLevel.RED);
            });
            agents.put("B", bAgent);

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));
            assertThat(r.getFinalResult()).isNotNull();
        }

        @Test
        @DisplayName("所有 Agent 都未注册 - 返回空结果且 finalResult=null")
        void shouldReturnEmptyWhenAllAgentsUnregistered() {
            OrchestrationRequest req = req(Arrays.asList("X", "Y"));
            OrchestrationResult r = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(r.getExecutedAgents()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
        }
    }
}
