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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 投票融合编排策略单元测试
 *
 * <p>覆盖：空 agentTypes / 并行执行 / 加权融合数学正确性 / 等级取最高 / suggestion 拼接 / 全部异常时 finalResult=null。
 * 测试 fuse() 方法时单独单元测试，不依赖线程池。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("VotingStrategy 投票融合策略测试")
class VotingStrategyTest {

    private ThreadPoolTaskExecutor executor;
    private VotingStrategy strategy;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("test-voting-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        strategy = new VotingStrategy(executor);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
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

    /** 构造带 suggestion 和 matchedRules 的 AgentResult */
    private AgentResult result(double score, double confidence, AgentAlertLevel level,
                                String suggestion, List<String> matchedRules) {
        return new AgentResult(AgentType.RISK_WARNING, level,
                BigDecimal.valueOf(score), BigDecimal.valueOf(confidence),
                suggestion, matchedRules, null);
    }

    /** 简化的 AgentResult（无 suggestion/matchedRules） */
    private AgentResult result(double score, double confidence, AgentAlertLevel level) {
        return result(score, confidence, level, null, null);
    }

    private OrchestrationRequest req(List<String> agentTypes) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setMode(OrchestrationMode.VOTING);
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
        @DisplayName("mode() 返回 VOTING")
        void shouldReturnVotingMode() {
            assertThat(strategy.mode()).isEqualTo(OrchestrationMode.VOTING);
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
        }

        @Test
        @DisplayName("agentTypes=空列表 - 返回空结果")
        void shouldReturnEmptyWhenAgentTypesEmpty() {
            OrchestrationRequest req = req(Collections.emptyList());
            OrchestrationResult r = strategy.apply(req, new HashMap<>(), new AgentBlackboard(null));
            assertThat(r.getFinalResult()).isNull();
        }
    }

    // ==================== 并行执行测试 ====================

    @Nested
    @DisplayName("并行执行测试")
    class ParallelExecutionTest {

        @Test
        @DisplayName("并行执行 3 个 Agent - 全部成功")
        void shouldExecuteAllAgentsInParallel() {
            OrchestrationRequest req = req(Arrays.asList("A", "B", "C"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.7, 0.85, AgentAlertLevel.YELLOW)));
            agents.put("C", mockAgent(result(0.9, 0.95, AgentAlertLevel.RED)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactlyInAnyOrder("A", "B", "C");
            assertThat(r.getAgentResults()).hasSize(3);
            assertThat(r.getAgentCount()).isEqualTo(3);
            assertThat(r.getFinalResult()).isNotNull();
        }

        @Test
        @DisplayName("全部 Agent 异常 - finalResult=null")
        void shouldReturnNullFinalResultWhenAllAgentsFail() {
            OrchestrationRequest req = req(Arrays.asList("BAD1", "BAD2"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("BAD1", throwingAgent("异常1"));
            agents.put("BAD2", throwingAgent("异常2"));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            // 异常 Agent 不会加入 executedAgents（Map.entry 不允许 null value）
            assertThat(r.getExecutedAgents()).isEmpty();
            assertThat(r.getAgentResults()).isEmpty();
            assertThat(r.getFinalResult()).isNull();
        }

        @Test
        @DisplayName("带权重的并行执行 - trace 中包含权重信息")
        void shouldRecordWeightsInTrace() {
            OrchestrationRequest req = req(Arrays.asList("A", "B"));
            Map<String, Double> weights = new HashMap<>();
            weights.put("A", 0.3);
            weights.put("B", 0.7);
            req.setWeights(weights);
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.7, 0.85, AgentAlertLevel.YELLOW)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getTrace()).hasSize(2);
            // trace 的 note 中包含权重值
            assertThat(r.getTrace().get(0).getNote()).contains("权重=");
        }

        @Test
        @DisplayName("跳过未注册的 Agent")
        void shouldSkipUnregisteredAgents() {
            OrchestrationRequest req = req(Arrays.asList("A", "MISSING", "B"));
            Map<String, Agent> agents = new HashMap<>();
            agents.put("A", mockAgent(result(0.5, 0.8, AgentAlertLevel.NORMAL)));
            agents.put("B", mockAgent(result(0.7, 0.85, AgentAlertLevel.YELLOW)));

            OrchestrationResult r = strategy.apply(req, agents, new AgentBlackboard(null));

            assertThat(r.getExecutedAgents()).containsExactlyInAnyOrder("A", "B");
        }
    }

    // ==================== fuse() 加权融合测试（不依赖线程池） ====================

    @Nested
    @DisplayName("fuse() 加权融合数学正确性测试")
    class FuseMathTest {

        @Test
        @DisplayName("agentResults=null - 返回 null")
        void shouldReturnNullWhenAgentResultsNull() {
            assertThat(strategy.fuse(null, new HashMap<>())).isNull();
        }

        @Test
        @DisplayName("agentResults=空 - 返回 null")
        void shouldReturnNullWhenAgentResultsEmpty() {
            assertThat(strategy.fuse(new HashMap<>(), new HashMap<>())).isNull();
        }

        @Test
        @DisplayName("全部 AgentResult 为 null - 返回 null")
        void shouldReturnNullWhenAllResultsAreNull() {
            Map<String, AgentResult> results = new HashMap<>();
            results.put("A", null);
            results.put("B", null);
            assertThat(strategy.fuse(results, new HashMap<>())).isNull();
        }

        @Test
        @DisplayName("加权 score 数学正确性 - 默认权重 1.0")
        void shouldCalculateWeightedScoreWithDefaultWeights() {
            // 公式：fusedScore = Σ(score * weight * confidence) / Σ(weight * confidence)
            // A: score=0.5, conf=0.8, weight=1.0 → 0.5*1.0*0.8=0.4, weight*conf=0.8
            // B: score=0.9, conf=0.95, weight=1.0 → 0.9*1.0*0.95=0.855, weight*conf=0.95
            // fusedScore = (0.4+0.855)/(0.8+0.95) = 1.255/1.75 = 0.7171...
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL));
            results.put("B", result(0.9, 0.95, AgentAlertLevel.RED));
            // fuse 直接使用 weights，不会自动填充默认值（apply 中才会填充）
            Map<String, Double> weights = new HashMap<>();
            weights.put("A", 1.0);
            weights.put("B", 1.0);

            AgentResult fused = strategy.fuse(results, weights);

            assertThat(fused).isNotNull();
            // 期望 0.7171（保留 2 位）
            double expected = (0.5 * 0.8 + 0.9 * 0.95) / (0.8 + 0.95);
            assertThat(fused.getScore().doubleValue())
                    .isCloseTo(expected, within(0.01));
            // confidence = Σ(conf*weight) / Σ(weight) = (0.8+0.95)/2 = 0.875
            double expectedConf = (0.8 + 0.95) / 2;
            assertThat(fused.getConfidence().doubleValue())
                    .isCloseTo(expectedConf, within(0.0001));
        }

        @Test
        @DisplayName("加权 score 数学正确性 - 自定义权重")
        void shouldCalculateWeightedScoreWithCustomWeights() {
            // A: score=0.5, conf=0.8, weight=0.3 → 0.5*0.3*0.8=0.12, weight*conf=0.24
            // B: score=0.9, conf=0.95, weight=0.7 → 0.9*0.7*0.95=0.5985, weight*conf=0.665
            // fusedScore = (0.12+0.5985)/(0.24+0.665) = 0.7185/0.905 = 0.7939...
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL));
            results.put("B", result(0.9, 0.95, AgentAlertLevel.RED));
            Map<String, Double> weights = new HashMap<>();
            weights.put("A", 0.3);
            weights.put("B", 0.7);

            AgentResult fused = strategy.fuse(results, weights);

            assertThat(fused).isNotNull();
            double expected = (0.5 * 0.3 * 0.8 + 0.9 * 0.7 * 0.95) / (0.3 * 0.8 + 0.7 * 0.95);
            assertThat(fused.getScore().doubleValue())
                    .isCloseTo(expected, within(0.01));
        }

        @Test
        @DisplayName("等级取最高 - RED > YELLOW > NORMAL")
        void shouldTakeHighestLevel() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL));
            results.put("B", result(0.9, 0.95, AgentAlertLevel.RED));
            results.put("C", result(0.6, 0.85, AgentAlertLevel.YELLOW));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
        }

        @Test
        @DisplayName("等级取最高 - YELLOW > NORMAL")
        void shouldTakeYellowOverNormal() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL));
            results.put("B", result(0.6, 0.85, AgentAlertLevel.YELLOW));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
        }

        @Test
        @DisplayName("suggestion 拼接所有 Agent 的建议")
        void shouldConcatenateSuggestions() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL, "建议A", null));
            results.put("B", result(0.9, 0.95, AgentAlertLevel.RED, "建议B", null));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused.getSuggestion()).contains("[A]");
            assertThat(fused.getSuggestion()).contains("建议A");
            assertThat(fused.getSuggestion()).contains("[B]");
            assertThat(fused.getSuggestion()).contains("建议B");
            assertThat(fused.getSuggestion()).contains("；");
        }

        @Test
        @DisplayName("suggestion 为空时不拼接 - 返回 null")
        void shouldReturnNullSuggestionWhenAllEmpty() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL, "", null));
            results.put("B", result(0.9, 0.95, AgentAlertLevel.RED, "  ", null));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused.getSuggestion()).isNull();
        }

        @Test
        @DisplayName("matchedRules 合并所有 Agent 的规则")
        void shouldMergeMatchedRules() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL, null,
                    Arrays.asList("rule1", "rule2")));
            results.put("B", result(0.9, 0.95, AgentAlertLevel.RED, null,
                    List.of("rule3")));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused.getMatchedRules()).containsExactly("rule1", "rule2", "rule3");
        }

        @Test
        @DisplayName("confidence=0 时按 1.0 计算（避免除零）")
        void shouldUseConfidenceOneWhenZero() {
            // confidence=0 时按 1.0 计算
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.0, AgentAlertLevel.NORMAL));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused).isNotNull();
            // score = (0.5*1*1)/(1*1) = 0.5
            assertThat(fused.getScore().doubleValue()).isCloseTo(0.5, within(0.01));
        }

        @Test
        @DisplayName("payload 包含 fusionMode / agentCount / weights")
        void shouldPopulatePayload() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL));
            Map<String, Double> weights = new HashMap<>();
            weights.put("A", 1.0);

            AgentResult fused = strategy.fuse(results, weights);

            assertThat(fused.getPayload()).isNotNull();
            assertThat(fused.getPayload().get("fusionMode")).isEqualTo("VOTING");
            assertThat(fused.getPayload().get("agentCount")).isEqualTo(1);
            assertThat(fused.getPayload().get("weights")).isSameAs(weights);
        }

        @Test
        @DisplayName("score 保留 2 位 / confidence 保留 4 位")
        void shouldScaleScoreAndConfidence() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL));
            results.put("B", result(0.9, 0.95, AgentAlertLevel.RED));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            // scale=2 表示保留 2 位小数
            assertThat(fused.getScore().scale()).isEqualTo(2);
            assertThat(fused.getConfidence().scale()).isEqualTo(4);
        }

        @Test
        @DisplayName("score=null 按 0 计算")
        void shouldTreatNullScoreAsZero() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            AgentResult ar = new AgentResult(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL,
                    null, BigDecimal.valueOf(0.8), null, null, null);
            results.put("A", ar);

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused).isNotNull();
            assertThat(fused.getScore().doubleValue()).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("confidence=null 按 0 计算（但 weight*conf=0 时按 1.0）")
        void shouldTreatNullConfidenceAsZero() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            AgentResult ar = new AgentResult(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL,
                    BigDecimal.valueOf(0.5), null, null, null, null);
            results.put("A", ar);

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused).isNotNull();
            // c=0, 按 1.0 计算: score=(0.5*1*1)/(1*1)=0.5
            assertThat(fused.getScore().doubleValue()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("融合后 agentType 置为 RISK_WARNING")
        void shouldSetAgentTypeToRiskWarning() {
            Map<String, AgentResult> results = new LinkedHashMap<>();
            results.put("A", result(0.5, 0.8, AgentAlertLevel.NORMAL));

            AgentResult fused = strategy.fuse(results, new HashMap<>());

            assertThat(fused.getAgentType()).isEqualTo(AgentType.RISK_WARNING);
        }
    }
}
