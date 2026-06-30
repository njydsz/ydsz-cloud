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
 * 利润预测 Agent 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ProfitForecastAgent 利润预测")
class ProfitForecastAgentTest {

    private final ProfitForecastAgent agent = new ProfitForecastAgent();

    @Test
    @DisplayName("类型-PROFIT_FORECAST")
    void type() {
        assertThat(agent.type()).isEqualTo(AgentType.PROFIT_FORECAST);
    }

    @Test
    @DisplayName("EAC 计算")
    void eacCalculation() {
        Map<String, Object> p = new HashMap<>();
        p.put("totalCost", new BigDecimal("600"));
        p.put("progressPct", new BigDecimal("50"));
        p.put("contractAmount", new BigDecimal("1000"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getPayload()).containsKey("eac");
        assertThat(((BigDecimal) r.getPayload().get("eac"))).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("健康度评分钳制 0-100")
    void healthScoreClamp() {
        Map<String, Object> p = new HashMap<>();
        p.put("totalCost", new BigDecimal("100"));
        p.put("progressPct", new BigDecimal("100"));
        p.put("contractAmount", new BigDecimal("500"));
        p.put("cpi", new BigDecimal("0.50"));
        p.put("spi", new BigDecimal("0.50"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        int health = ((Number) r.getPayload().get("healthScore")).intValue();
        assertThat(health).isBetween(0, 100);
    }

    @Test
    @DisplayName("理想情况 接近 100")
    void healthScoreIdeal() {
        Map<String, Object> p = new HashMap<>();
        p.put("totalCost", new BigDecimal("200"));
        p.put("progressPct", new BigDecimal("50"));
        p.put("contractAmount", new BigDecimal("1000"));
        p.put("cpi", new BigDecimal("1.5"));
        p.put("spi", new BigDecimal("1.5"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        int health = ((Number) r.getPayload().get("healthScore")).intValue();
        // margin=80% -> 50pts + cpi>=1 -> 25pts + spi>=1 -> 25pts = 100
        assertThat(health).isGreaterThanOrEqualTo(95);
    }

    @Test
    @DisplayName("零进度 防止除零")
    void zeroProgress() {
        Map<String, Object> p = new HashMap<>();
        p.put("totalCost", new BigDecimal("100"));
        p.put("progressPct", BigDecimal.ZERO);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getPayload()).containsKey("eac");
        assertThat(((BigDecimal) r.getPayload().get("eac"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("建议包含 EAC 和健康度")
    void suggestionFormat() {
        Map<String, Object> p = new HashMap<>();
        p.put("totalCost", new BigDecimal("100"));
        p.put("progressPct", new BigDecimal("50"));
        p.put("contractAmount", new BigDecimal("500"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getSuggestion()).contains("EAC");
        assertThat(r.getSuggestion()).contains("毛利率");
    }

    @Test
    @DisplayName("空参数 安全降级")
    void emptyParams() {
        AgentContext ctx = new AgentContext();
        ctx.setParams(new HashMap<>());
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAgentType()).isEqualTo(AgentType.PROFIT_FORECAST);
        // 健康度=cpi+spi=50 分 < 75，YELLOW
        assertThat(r.getAlertLevel()).isIn(AgentAlertLevel.YELLOW, AgentAlertLevel.RED);
    }

    @Test
    @DisplayName("理想情况 接近 100 (新)")
    void healthScoreIdealCase() {
        Map<String, Object> p = new HashMap<>();
        p.put("totalCost", new BigDecimal("200"));
        p.put("progressPct", new BigDecimal("50"));
        p.put("contractAmount", new BigDecimal("1000"));
        p.put("cpi", new BigDecimal("1.5"));
        p.put("spi", new BigDecimal("1.5"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        int health = ((Number) r.getPayload().get("healthScore")).intValue();
        // margin=80% -> 50pts + cpi>=1 -> 25pts + spi>=1 -> 25pts = 100
        assertThat(health).isGreaterThanOrEqualTo(95);
    }
}
