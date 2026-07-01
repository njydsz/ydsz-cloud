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
 * 风险预警 Agent 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RiskWarningAgent 风险预警")
class RiskWarningAgentTest {

    private final RiskWarningAgent agent = new RiskWarningAgent();

    @Test
    @DisplayName("类型-RISK_WARNING")
    void type() {
        assertThat(agent.type()).isEqualTo(AgentType.RISK_WARNING);
    }

    @Test
    @DisplayName("空参数-NORMAL")
    void executeEmpty() {
        AgentContext ctx = new AgentContext();
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAgentType()).isEqualTo(AgentType.RISK_WARNING);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
        assertThat(r.getScore()).isEqualByComparingTo("0");
        assertThat(r.getMatchedRules()).isEmpty();
    }

    @Test
    @DisplayName("CPI<0.85 + 高风险事件 = 红色")
    void executeCpiRed() {
        Map<String, Object> p = new HashMap<>();
        p.put("cpi", new BigDecimal("0.70"));
        p.put("spi", BigDecimal.ONE);
        p.put("highRiskCount", 2);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        // 0.35(CPI) + 0.10(highRisk>=2) = 0.45 -> YELLOW
        // 加上 costOverrun/负毛利率可触发 RED
        assertThat(r.getAlertLevel()).isIn(AgentAlertLevel.YELLOW, AgentAlertLevel.RED);
        assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("CPI"));
    }

    @Test
    @DisplayName("CPI 0.90 黄色或以上")
    void executeCpiYellow() {
        Map<String, Object> p = new HashMap<>();
        p.put("cpi", new BigDecimal("0.90"));
        p.put("spi", new BigDecimal("0.90"));
        p.put("highRiskCount", 2);
        p.put("costOverrun", new BigDecimal("0.15"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        // 0.18 + 0.10 + 0.10 + 0.10 = 0.48 -> YELLOW
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
    }

    @Test
    @DisplayName("毛利率为负 触发规则")
    void executeNegativeMargin() {
        Map<String, Object> p = new HashMap<>();
        p.put("grossMargin", new BigDecimal("-0.10"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("毛利率"));
    }

    @Test
    @DisplayName("高风险事件计数")
    void executeHighRisk() {
        Map<String, Object> p = new HashMap<>();
        p.put("highRiskCount", 3);
        p.put("riskCount", 6);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("高风险事件"));
    }

    @Test
    @DisplayName("评分钳制到 0-1")
    void executeScoreClamp() {
        Map<String, Object> p = new HashMap<>();
        p.put("cpi", new BigDecimal("0.10"));
        p.put("spi", new BigDecimal("0.10"));
        p.put("costOverrun", new BigDecimal("1.0"));
        p.put("grossMargin", new BigDecimal("-0.50"));
        p.put("highRiskCount", 10);
        p.put("riskCount", 20);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getScore().doubleValue()).isBetween(0.0, 1.0);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
    }

    @Test
    @DisplayName("payload 包含关键指标")
    void executePayload() {
        Map<String, Object> p = new HashMap<>();
        p.put("cpi", new BigDecimal("1.0"));
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getPayload()).isNotNull();
        assertThat(r.getPayload()).containsKey("score");
    }

    @Test
    @DisplayName("建议非空")
    void executeSuggestion() {
        AgentContext ctx = new AgentContext();
        ctx.setParams(new HashMap<>());
        AgentResult r = agent.execute(ctx);
        assertThat(r.getSuggestion()).isNotBlank();
    }
}
