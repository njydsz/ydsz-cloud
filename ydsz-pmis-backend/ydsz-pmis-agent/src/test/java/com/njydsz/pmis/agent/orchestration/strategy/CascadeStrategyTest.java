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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 级联编排策略测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("CascadeStrategy 级联编排")
class CascadeStrategyTest {

    private final CascadeStrategy strategy = new CascadeStrategy();

    @Test
    @DisplayName("第 1 个 Agent 达标 - 提前终止")
    void firstReached() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "70", "0.95");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.RED, "90", "0.90");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        req.setConfidenceThreshold(0.85);
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING");
        assertThat(r.getFinalResult().getAgentType()).isEqualTo(AgentType.RISK_WARNING);
        assertThat(r.getNote()).contains("达标提前终止");
    }

    @Test
    @DisplayName("全部未达标 - 取最后一个")
    void noneReached() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0.30");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.YELLOW, "60", "0.40");
        Agent c = stubAgent(AgentType.WIN_RATE_PREDICT, AgentAlertLevel.RED, "90", "0.80");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST", "WIN_RATE_PREDICT"));
        req.setConfidenceThreshold(0.85);
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b, "WIN_RATE_PREDICT", c),
                new AgentBlackboard());
        assertThat(r.getExecutedAgents()).hasSize(3);
        assertThat(r.getFinalResult().getAgentType()).isEqualTo(AgentType.WIN_RATE_PREDICT);
        assertThat(r.getNote()).contains("不达标");
    }

    @Test
    @DisplayName("第 2 个达标 - 第 3 个不跑")
    void secondReached() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0.30");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "70", "0.90");
        Agent c = stubAgent(AgentType.WIN_RATE_PREDICT, AgentAlertLevel.RED, "90", "0.95");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST", "WIN_RATE_PREDICT"));
        req.setConfidenceThreshold(0.85);
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b, "WIN_RATE_PREDICT", c),
                new AgentBlackboard());
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING", "PROFIT_FORECAST");
        assertThat(r.getFinalResult().getAgentType()).isEqualTo(AgentType.PROFIT_FORECAST);
        assertThat(r.getNote()).contains("第 2");
    }

    @Test
    @DisplayName("未指定 threshold - 缺省 0.85")
    void defaultThreshold() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0.80");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "50", "0.95");
        OrchestrationResult r = strategy.apply(req(List.of("RISK_WARNING", "PROFIT_FORECAST")),
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING", "PROFIT_FORECAST");
        assertThat(r.getFinalResult().getAgentType()).isEqualTo(AgentType.PROFIT_FORECAST);
    }

    @Test
    @DisplayName("Agent 抛错 - 跳到下一个 不中断")
    void exceptionSkipped() {
        Agent good = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0.40");
        Agent bad = new Agent() {
            @Override
            public AgentType type() { return AgentType.PROFIT_FORECAST; }
            @Override
            public AgentResult execute(AgentContext ctx) { throw new RuntimeException("boom"); }
        };
        Agent last = stubAgent(AgentType.WIN_RATE_PREDICT, AgentAlertLevel.NORMAL, "50", "0.95");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST", "WIN_RATE_PREDICT"));
        req.setConfidenceThreshold(0.85);
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", good, "PROFIT_FORECAST", bad, "WIN_RATE_PREDICT", last),
                new AgentBlackboard());
        // good 失败 0.4<0.85 → bad 抛错 → last 0.95>0.85 提前终止
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING", "WIN_RATE_PREDICT");
        assertThat(r.getFinalResult().getAgentType()).isEqualTo(AgentType.WIN_RATE_PREDICT);
    }

    @Test
    @DisplayName("trace 包含每个 Agent 决策 + 达标 / 未达标 note")
    void traceRecorded() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0.50");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "60", "0.95");
        OrchestrationResult r = strategy.apply(req(List.of("RISK_WARNING", "PROFIT_FORECAST")),
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(r.getTrace()).hasSize(2);
        assertThat(r.getTrace().get(0).getNote()).contains("未达标");
        assertThat(r.getTrace().get(1).getNote()).contains("达标");
    }

    @Test
    @DisplayName("未注册 Agent - 跳过")
    void unregisteredSkipped() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0.95");
        OrchestrationResult r = strategy.apply(req(List.of("RISK_WARNING", "MISSING")),
                Map.of("RISK_WARNING", a), new AgentBlackboard());
        assertThat(r.getExecutedAgents()).containsExactly("RISK_WARNING");
    }

    @Test
    @DisplayName("空 agentTypes")
    void emptyTypes() {
        OrchestrationResult r = strategy.apply(req(List.of()), Map.of(), new AgentBlackboard());
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.CASCADE);
        assertThat(r.getNote()).contains("未指定");
    }

    @Test
    @DisplayName("scratch 注入下游 params.upstream.*")
    void upstreamInjected() {
        final List<String> seen = new java.util.ArrayList<>();
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0.30");
        Agent b = new Agent() {
            @Override
            public AgentType type() { return AgentType.PROFIT_FORECAST; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                if (ctx.getParams() != null) seen.addAll(ctx.getParams().keySet());
                AgentResult r = new AgentResult();
                r.setAgentType(AgentType.PROFIT_FORECAST);
                r.setAlertLevel(AgentAlertLevel.NORMAL);
                r.setScore(new BigDecimal("60"));
                r.setConfidence(new BigDecimal("0.95"));
                return r;
            }
        };
        strategy.apply(req(List.of("RISK_WARNING", "PROFIT_FORECAST")),
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(seen).contains("upstream.RISK_WARNING");
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

    private Agent stubAgent(AgentType t, AgentAlertLevel l, String score, String conf) {
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
                return r;
            }
        };
    }
}
