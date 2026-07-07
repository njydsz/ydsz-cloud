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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 并行编排策略单元测试
 *
 * <p>覆盖：空 agentTypes / 并行执行多个 Agent / 选 score 最高的为 finalResult / 某 Agent 异常时其他正常合并。
 * 注意：被测类构造函数需要注入 ThreadPoolTaskExecutor，测试时手动 new 一个并 initialize（不能 mock，
 * 因为 CompletableFuture.supplyAsync(executor) 需要真实线程池）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ParallelStrategy 并行编排策略测试")
class ParallelStrategyTest {

    private ThreadPoolTaskExecutor executor;
    private ParallelStrategy strategy;

    @BeforeEach
    void setUp() {
        // 手动 new 真实线程池并 initialize（不能 mock）
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("test-agent-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        strategy = new ParallelStrategy(executor);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    // ==================== 辅助方法 ====================

    /** 构造返回固定结果的 Agent */
    private Agent mockAgent(AgentResult result) {
        Agent agent = mock(Agent.class);
        when(agent.execute(any(AgentContext.class))).thenReturn(result);
        return agent;
    }

    /** 构造抛异常的 Agent */
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
        req.setMode(OrchestrationMode.PARALLEL);
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
        @DisplayName("mode() 返回 PARALLEL")
        void shouldReturnParallelMode() {
            assertThat(strategy.mode()).isEqualTo(OrchestrationMode.PARALLEL);
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
            assertThat(r.getExecutedAgents()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
            assertThat(r.getNote()).isEqualTo("未指定参与编排的 Agent");
            assertThat(r.getMode()).isEqualTo(OrchestrationMode.PARALLEL);
        }

        @Test
        @DisplayName("agentTypes=空列表 - 返回空结果")
        void shouldReturnEmptyWhenAgentTypesEmpty() {
            OrchestrationRequest req = req(Collections.emptyList());
            OrchestrationResult r = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(r.getAgentResults()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
            assertThat(r.getNote()).isEqualTo("未指定参与编排的 Agent");
        }
    }

    // ==================== 并行执行测试 ====================

    @Nested
    @DisplayName("并行执行测试")
    class ParallelExecutionTest {

        @Test
        @DisplayName("并行执行 3 个 Agent - 全部成功")
        void shouldExecuteAllAgentsInParallel() throws InterruptedException {
            OrchestrationRequest req = req(Arrays.asList("A", "B", "C"));
            Map<String, Agent> agents = new HashMap<>();
            AtomicInteger counter = new AtomicInteger(0);
            Agent a = mock(Agent.class);
            when(a.execute(any())).thenAnswer(inv -> {
                counter.incrementAndGet();
                Thread.sleep(50);
                return result(0.5, 0.8, AgentAlertLevel.NORMAL);
            });
            Agent b = mock(Agent.class);
            when(b.execute(any())).thenAnswer(inv -> {
                counter.incrementAndGet();
                Thread.sleep(50);
                return result(0.7, 0.85, AgentAlertLevel.YELLOW);
            });
            Agent c = mock(Agent.class);
            when(c.execute(any())).thenAnswer(inv -> {
                counter.incrementAndGet();
                Thread.sleep(50);
                return result(0.9, 0.95, AgentAlertLevel.RED);
            });
            agents.put("A", a);
            agents.put("B", b);
            agents.put("C", c);

            long start = System.currentTimeMillis();
            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(r.getExecutedAgents()).containsExactlyInAnyOrder("A", "B", "C");
            assertThat(r.getAgentResults()).hasSize(3);
            assertThat(r.getAgentCount()).isEqualTo(3);
            assertThat(r.getNote()).contains("并行执行完成");
            // 3 个 50ms 任务并行执行，总耗时应远小于 150ms（验证并行性）
            assertThat(elapsed).isLessThan(130L);
        }

        @Test
        @DisplayName("选 score 最高的为 finalResult")
        void shouldSelectHighestScoreAsFinalResult() {
            OrchestrationRequest req = req(Arrays.asList("LOW", "HIGH", "MID"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("LOW", mockAgent(result(0.3, 0.7, AgentAlertLevel.NORMAL)));
            agents.put("HIGH", mockAgent(result(0.95, 0.95, AgentAlertLevel.RED)));
            agents.put("MID", mockAgent(result(0.6, 0.85, AgentAlertLevel.YELLOW)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getFinalResult()).isNotNull();
            assertThat(r.getFinalResult().getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.95));
            assertThat(r.getFinalResult().getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
            assertThat(r.getNote()).contains("HIGH");
        }

        @Test
        @DisplayName("score 相同时比较 confidence - 选 confidence 高的")
        void shouldCompareConfidenceWhenScoreEqual() {
            OrchestrationRequest req = req(Arrays.asList("A", "B"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.8, 0.7, AgentAlertLevel.YELLOW)));
            agents.put("B", mockAgent(result(0.8, 0.95, AgentAlertLevel.YELLOW)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getFinalResult()).isNotNull();
            assertThat(r.getFinalResult().getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.95));
            assertThat(r.getNote()).contains("B");
        }

        @Test
        @DisplayName("某 Agent 异常时其他 Agent 正常合并")
        void shouldMergeOtherAgentsWhenOneFails() {
            OrchestrationRequest req = req(Arrays.asList("A", "BAD", "C"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("BAD", throwingAgent("并行异常"));
            agents.put("C", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            // 3 个都尝试了（包括 BAD）
            assertThat(r.getExecutedAgents()).containsExactlyInAnyOrder("A", "BAD", "C");
            // 只有 A 和 C 有结果（BAD 返回 null）
            assertThat(r.getAgentResults()).containsOnlyKeys("A", "C");
            assertThat(r.getAgentCount()).isEqualTo(3);
            // finalResult 是 A 或 C 中 score 最高的（C）
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

            assertThat(r.getExecutedAgents()).containsExactlyInAnyOrder("BAD1", "BAD2");
            assertThat(r.getAgentResults()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
            assertThat(r.getNote()).contains("无");
        }

        @Test
        @DisplayName("跳过未注册的 Agent")
        void shouldSkipUnregisteredAgents() {
            OrchestrationRequest req = req(Arrays.asList("A", "MISSING", "B"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactlyInAnyOrder("A", "B");
            assertThat(r.getAgentResults()).hasSize(2);
        }

        @Test
        @DisplayName("facts=null 时不抛 NPE")
        void shouldHandleNullFacts() {
            OrchestrationRequest req = req(List.of("A"));
            req.setFacts(null);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));
            assertThat(r.getAgentResults()).hasSize(1);
        }

        @Test
        @DisplayName("所有 Agent 都未注册 - 返回空结果")
        void shouldReturnEmptyWhenAllAgentsUnregistered() {
            OrchestrationRequest req = req(Arrays.asList("X", "Y"));
            OrchestrationResult r = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(r.getExecutedAgents()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
        }
    }
}
