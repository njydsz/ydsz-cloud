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
 * 投票融合策略测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("VotingStrategy 投票融合")
class VotingStrategyTest {

    private final VotingStrategy strategy = new VotingStrategy();

    @Test
    @DisplayName("加权融合 - score / confidence / level")
    void weightedFusion() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.80", "A-sugg");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.RED, "90", "0.50", "B-sugg");
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        req.setWeights(Map.of("RISK_WARNING", 0.6, "PROFIT_FORECAST", 0.4));
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        AgentResult fused = r.getFinalResult();
        // 60*0.6*0.8=28.8, 90*0.4*0.5=18 → sum=46.8; weight=0.6*0.8+0.4*0.5=0.68; score=68.82
        assertThat(fused.getScore().doubleValue()).isBetween(68d, 69d);
        // 0.8*0.6 + 0.5*0.4 = 0.68; totalW = 1.0; conf = 0.68
        assertThat(fused.getConfidence().doubleValue()).isBetween(0.67d, 0.69d);
        // level = RED
        assertThat(fused.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
        // suggestion 拼接
        assertThat(fused.getSuggestion()).contains("RISK_WARNING", "PROFIT_FORECAST");
    }

    @Test
    @DisplayName("未提供 weights - 缺省 1.0 平均")
    void noWeights() {
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.50", null);
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "80", "0.50", null);
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        // 60*0.5=30, 80*0.5=40, sum=70; weight=1.0 → score=70
        assertThat(r.getFinalResult().getScore().doubleValue()).isEqualTo(70d);
    }

    @Test
    @DisplayName("fuse 直接调用 - 边界 + payload")
    void fuseDirect() {
        Map<String, AgentResult> in = new HashMap<>();
        in.put("A", stubResult(AgentType.RISK_WARNING, AgentAlertLevel.YELLOW, "50", "0.50", "sa"));
        in.put("B", stubResult(AgentType.PROFIT_FORECAST, AgentAlertLevel.RED, "70", "0.90", "sb"));
        AgentResult fused = strategy.fuse(in, Map.of("A", 0.5, "B", 0.5));
        assertThat(fused).isNotNull();
        assertThat(fused.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
        assertThat(fused.getPayload()).containsEntry("fusionMode", "VOTING");
        assertThat(fused.getPayload()).containsEntry("agentCount", 2);
    }

    @Test
    @DisplayName("fuse - 空入参返回 null")
    void fuseEmpty() {
        assertThat(strategy.fuse(null, Map.of())).isNull();
        assertThat(strategy.fuse(new HashMap<>(), Map.of())).isNull();
    }

    @Test
    @DisplayName("fuse - confidence=0 时 score 项退化为 weight * 1")
    void fuseZeroConfidence() {
        Map<String, AgentResult> in = new HashMap<>();
        in.put("A", stubResult(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "50", "0", null));
        in.put("B", stubResult(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "80", "0", null));
        AgentResult fused = strategy.fuse(in, Map.of("A", 0.5, "B", 0.5));
        // 50*0.5*1=25, 80*0.5*1=40, sum=65, weight=1.0, score=65
        assertThat(fused.getScore().doubleValue()).isEqualTo(65d);
    }

    @Test
    @DisplayName("Agent 抛错 - 跳到下一个,trace 标注异常")
    void exceptionTolerated() {
        Agent good = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.50", "ok");
        Agent bad = new Agent() {
            @Override
            public AgentType type() { return AgentType.PROFIT_FORECAST; }
            @Override
            public AgentResult execute(AgentContext ctx) { throw new RuntimeException("boom"); }
        };
        OrchestrationRequest req = req(List.of("RISK_WARNING", "PROFIT_FORECAST"));
        OrchestrationResult r = strategy.apply(req,
                Map.of("RISK_WARNING", good, "PROFIT_FORECAST", bad), new AgentBlackboard());
        assertThat(r.getAgentResults()).containsOnlyKeys("RISK_WARNING");
        assertThat(r.getFinalResult().getScore().doubleValue()).isEqualTo(60d);
        assertThat(r.getTrace()).anyMatch(t -> t.getNote() != null && t.getNote().contains("异常"));
    }

    @Test
    @DisplayName("matchedRules 合并")
    void rulesAggregated() {
        AgentResult ra = stubResult(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.5", null);
        ra.setMatchedRules(List.of("R1", "R2"));
        AgentResult rb = stubResult(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "60", "0.5", null);
        rb.setMatchedRules(List.of("R3"));
        Agent sa = stubAgentFrom(AgentType.RISK_WARNING, ra);
        Agent sb = stubAgentFrom(AgentType.PROFIT_FORECAST, rb);
        OrchestrationResult r = strategy.apply(req(List.of("RISK_WARNING", "PROFIT_FORECAST")),
                Map.of("RISK_WARNING", sa, "PROFIT_FORECAST", sb), new AgentBlackboard());
        assertThat(r.getFinalResult().getMatchedRules()).containsExactlyInAnyOrder("R1", "R2", "R3");
    }

    @Test
    @DisplayName("空 agentTypes")
    void emptyTypes() {
        OrchestrationResult r = strategy.apply(req(List.of()), Map.of(), new AgentBlackboard());
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.VOTING);
        assertThat(r.getNote()).contains("未指定");
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
        return stubAgentFrom(t, stubResult(t, l, score, conf, sugg));
    }

    private Agent stubAgentFrom(AgentType t, AgentResult fixed) {
        return new Agent() {
            @Override
            public AgentType type() { return t; }
            @Override
            public AgentResult execute(AgentContext ctx) { return fixed; }
        };
    }

    private AgentResult stubResult(AgentType t, AgentAlertLevel l, String score, String conf, String sugg) {
        AgentResult r = new AgentResult();
        r.setAgentType(t);
        r.setAlertLevel(l);
        r.setScore(new BigDecimal(score));
        r.setConfidence(new BigDecimal(conf));
        r.setSuggestion(sugg);
        return r;
    }
}
