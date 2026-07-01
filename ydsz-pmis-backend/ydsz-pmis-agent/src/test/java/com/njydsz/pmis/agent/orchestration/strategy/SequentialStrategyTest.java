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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 顺序编排策略测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SequentialStrategy 顺序编排")
class SequentialStrategyTest {

    private final SequentialStrategy strategy = new SequentialStrategy();

    @Test
    @DisplayName("空 agentTypes 直接返回")
    void emptyTypes() {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setAgentTypes(List.of());
        OrchestrationResult r = strategy.apply(req, Map.of(), new AgentBlackboard());
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.SEQUENTIAL);
        assertThat(r.getAgentResults()).isEmpty();
        assertThat(r.getNote()).contains("未指定");
    }

    @Test
    @DisplayName("agentTypes 为 null 直接返回")
    void nullTypes() {
        OrchestrationRequest req = new OrchestrationRequest();
        OrchestrationResult r = strategy.apply(req, Map.of(), new AgentBlackboard());
        assertThat(r.getAgentCount()).isZero();
    }

    @Test
    @DisplayName("按顺序执行 - finalResult = 最后一个 Agent 输出")
    void inOrder() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.YELLOW, "70", "0.80", "first");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.RED, "90", "0.95", "second");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        OrchestrationResult r = strategy.apply(req, Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING", "PROFIT_FORECAST");
        assertThat(r.getFinalResult().getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
        assertThat(r.getAgentResults()).containsKeys("RISK_WARNING", "PROFIT_FORECAST");
    }

    @Test
    @DisplayName("上游 scratch 注入下游 params.upstream.*")
    void upstreamInjected() {
        final List<String> seenB = new ArrayList<>();
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.YELLOW, "70", "0.80", "first");
        Agent b = new Agent() {
            @Override
            public AgentType type() { return AgentType.PROFIT_FORECAST; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                if (ctx.getParams() != null) {
                    seenB.addAll(ctx.getParams().keySet());
                }
                AgentResult r = new AgentResult();
                r.setAgentType(AgentType.PROFIT_FORECAST);
                r.setAlertLevel(AgentAlertLevel.NORMAL);
                r.setScore(new BigDecimal("60"));
                r.setConfidence(new BigDecimal("0.50"));
                r.setSuggestion("seen");
                return r;
            }
        };
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        Map<String, Object> facts = new HashMap<>();
        facts.put("origin", 1);
        req.setFacts(facts);
        strategy.apply(req, Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(seenB).contains("origin", "upstream.RISK_WARNING");
    }

    @Test
    @DisplayName("未注册 Agent 被跳过 不抛错")
    void unregisteredSkipped() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.60", "ok");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "UNKNOWN"));
        OrchestrationResult r = strategy.apply(req, Map.of("RISK_WARNING", a), new AgentBlackboard());
        // UNKNOWN 不在 agents 注册表中，被跳过；只有 RISK_WARNING 进入执行链
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING");
        assertThat(r.getAgentResults()).containsOnlyKeys("RISK_WARNING");
    }

    @Test
    @DisplayName("单个 Agent 异常 - 跳到下一个 不中断")
    void exceptionSkipped() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.60", "ok");
        Agent bad = new Agent() {
            @Override
            public AgentType type() { return AgentType.PROFIT_FORECAST; }
            @Override
            public AgentResult execute(AgentContext ctx) { throw new RuntimeException("boom"); }
        };
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", bad), new AgentBlackboard());
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING");
        // finalResult 取最后成功 Agent 输出
        assertThat(r.getFinalResult().getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
        assertThat(r.getTrace()).anyMatch(t -> t.getNote() != null && t.getNote().contains("异常"));
    }

    @Test
    @DisplayName("trace 包含每个 Agent 的决策路径")
    void traceRecorded() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.60", "ok");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "60", "0.60", "ok");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(r.getTrace()).hasSize(2);
        assertThat(r.getTrace().get(0).getMode()).isEqualTo("SEQUENTIAL");
    }

    private OrchestrationRequest req(List<String> types) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setCallerId(1L);
        req.setCallerName("tester");
        req.setSource("TEST");
        req.setAgentTypes(types);
        req.setFacts(new HashMap<>());
        return req;
    }

    private Agent stubAgent(AgentType t, AgentAlertLevel l, String score, String conf, String sugg) {
        return new Agent() {
            @Override
            public AgentType type() { return t; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                AgentResult r = new AgentResult();
                r.setAgentType(t);
                r.setAlertLevel(l);
                r.setScore(new BigDecimal(score));
                r.setConfidence(new BigDecimal(conf));
                r.setSuggestion(sugg);
                return r;
            }
        };
    }
}
