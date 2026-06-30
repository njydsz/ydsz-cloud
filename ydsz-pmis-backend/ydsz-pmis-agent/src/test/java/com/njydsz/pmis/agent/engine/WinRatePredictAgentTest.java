package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商机赢率预测 Agent 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("WinRatePredictAgent 赢率预测")
class WinRatePredictAgentTest {

    private final WinRatePredictAgent agent = new WinRatePredictAgent();

    @Test
    @DisplayName("类型-WIN_RATE_PREDICT")
    void type() {
        assertThat(agent.type()).isEqualTo(AgentType.WIN_RATE_PREDICT);
    }

    @Test
    @DisplayName("空参数 默认值")
    void empty() {
        AgentContext ctx = new AgentContext();
        ctx.setParams(new HashMap<>());
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAgentType()).isEqualTo(AgentType.WIN_RATE_PREDICT);
        BigDecimal winRate = (BigDecimal) r.getPayload().get("winRate");
        assertThat(winRate).isNotNull();
        assertThat(winRate.doubleValue()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("高资质 + 谈判阶段 = 高赢率")
    void highWinRate() {
        Map<String, Object> p = new HashMap<>();
        p.put("customerCredit", new BigDecimal("0.9"));
        p.put("historyScore", new BigDecimal("0.8"));
        p.put("competitionScore", new BigDecimal("0.7"));
        p.put("stage", "NEGOTIATION");
        p.put("amount", new BigDecimal("10000000"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
        BigDecimal winRate = (BigDecimal) r.getPayload().get("winRate");
        assertThat(winRate.doubleValue()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    @DisplayName("低资质 + 早期阶段 = 低赢率")
    void lowWinRate() {
        Map<String, Object> p = new HashMap<>();
        p.put("customerCredit", new BigDecimal("0.1"));
        p.put("historyScore", new BigDecimal("0.1"));
        p.put("competitionScore", new BigDecimal("0.1"));
        p.put("stage", "DISCOVERY");
        p.put("amount", new BigDecimal("100000"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
    }

    @Test
    @DisplayName("中型项目金额得分高")
    void midSizeAmount() {
        Map<String, Object> p = new HashMap<>();
        p.put("amount", new BigDecimal("10000000")); // 1000万
        p.put("customerCredit", new BigDecimal("0.9"));
        p.put("historyScore", new BigDecimal("0.9"));
        p.put("competitionScore", new BigDecimal("0.9"));
        p.put("stage", "NEGOTIATION");
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        BigDecimal winRate = (BigDecimal) r.getPayload().get("winRate");
        // 0.9*0.2 + 0.9*0.2 + 0.9*0.2 + 0.30 + 0.7*0.1 = 0.18*3+0.30+0.07 = 0.91
        assertThat(winRate.doubleValue()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("赢率钳制 0-1")
    void clampRange() {
        Map<String, Object> p = new HashMap<>();
        p.put("customerCredit", new BigDecimal("2.0")); // 超出
        p.put("stage", "NEGOTIATION");
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        BigDecimal winRate = (BigDecimal) r.getPayload().get("winRate");
        assertThat(winRate.doubleValue()).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("建议含百分比")
    void suggestionHasPercent() {
        AgentContext ctx = new AgentContext();
        ctx.setParams(new HashMap<>());
        AgentResult r = agent.execute(ctx);
        assertThat(r.getSuggestion()).contains("%");
    }

    @Test
    @DisplayName("matchedRules 5 项")
    void matchedRulesFive() {
        AgentContext ctx = new AgentContext();
        ctx.setParams(new HashMap<>());
        AgentResult r = agent.execute(ctx);
        assertThat(r.getMatchedRules()).hasSize(5);
    }
}
