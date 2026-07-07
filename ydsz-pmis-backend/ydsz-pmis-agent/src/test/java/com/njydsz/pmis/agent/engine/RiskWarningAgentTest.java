package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目风险预警 Agent 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>CPI/SPI/成本超支/毛利率/高风险数/风险数 各维度评分</li>
 *   <li>告警等级阈值（RED/YELLOW/NORMAL）</li>
 *   <li>置信度计算公式</li>
 *   <li>空 params 使用默认值</li>
 *   <li>String 类型数字转换</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RiskWarningAgent 项目风险预警 Agent 测试")
class RiskWarningAgentTest {

    private final RiskWarningAgent agent = new RiskWarningAgent();

    // ==================== 辅助方法 ====================

    /** 构造 AgentContext */
    private AgentContext ctx(Map<String, Object> params) {
        AgentContext ctx = new AgentContext();
        ctx.setBizType("project");
        ctx.setBizId("P001");
        ctx.setBizRef("PRJ-001");
        ctx.setCallerId("U001");
        ctx.setCallerName("张三");
        ctx.setSource("unit-test");
        ctx.setParams(params);
        return ctx;
    }

    /** 构造 params builder */
    private Map<String, Object> params() {
        return new HashMap<>();
    }

    // ==================== 基础属性测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("type() 返回 RISK_WARNING")
        void shouldReturnRiskWarningType() {
            assertThat(agent.type()).isEqualTo(AgentType.RISK_WARNING);
        }
    }

    // ==================== CPI 评分测试 ====================

    @Nested
    @DisplayName("CPI 评分测试")
    class CpiTest {

        @Test
        @DisplayName("cpi<0.85 加 0.35 分")
        void shouldAdd035WhenCpiBelow085() {
            Map<String, Object> p = params();
            p.put("cpi", 0.80);
            AgentResult r = agent.execute(ctx(p));

            // 仅 cpi=0.80 触发 0.35
            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.3500"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("CPI<0.85"));
        }

        @Test
        @DisplayName("cpi<0.95 加 0.18 分")
        void shouldAdd018WhenCpiBelow095() {
            Map<String, Object> p = params();
            p.put("cpi", 0.90);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.1800"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("CPI<0.95"));
        }

        @Test
        @DisplayName("cpi>=0.95 不加分")
        void shouldNotAddScoreWhenCpiAbove095() {
            Map<String, Object> p = params();
            p.put("cpi", 0.95);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
            assertThat(r.getMatchedRules()).isEmpty();
        }
    }

    // ==================== SPI 评分测试 ====================

    @Nested
    @DisplayName("SPI 评分测试")
    class SpiTest {

        @Test
        @DisplayName("spi<0.85 加 0.20 分")
        void shouldAdd020WhenSpiBelow085() {
            Map<String, Object> p = params();
            p.put("spi", 0.80);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.2000"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("SPI<0.85"));
        }

        @Test
        @DisplayName("spi<0.95 加 0.10 分")
        void shouldAdd010WhenSpiBelow095() {
            Map<String, Object> p = params();
            p.put("spi", 0.90);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.1000"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("SPI<0.95"));
        }

        @Test
        @DisplayName("spi>=0.95 不加分")
        void shouldNotAddScoreWhenSpiAbove095() {
            Map<String, Object> p = params();
            p.put("spi", 0.95);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
        }
    }

    // ==================== 成本超支评分测试 ====================

    @Nested
    @DisplayName("成本超支评分测试")
    class CostOverrunTest {

        @Test
        @DisplayName("costOverrun>=0.20 加 0.20 分")
        void shouldAdd020WhenCostOverrunAbove20() {
            Map<String, Object> p = params();
            p.put("costOverrun", 0.25);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.2000"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("成本超支≥20%"));
        }

        @Test
        @DisplayName("costOverrun>=0.10 加 0.10 分")
        void shouldAdd010WhenCostOverrunAbove10() {
            Map<String, Object> p = params();
            p.put("costOverrun", 0.15);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.1000"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("成本超支≥10%"));
        }

        @Test
        @DisplayName("costOverrun<0.10 不加分")
        void shouldNotAddScoreWhenCostOverrunBelow10() {
            Map<String, Object> p = params();
            p.put("costOverrun", 0.05);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
        }
    }

    // ==================== 毛利率评分测试 ====================

    @Nested
    @DisplayName("毛利率评分测试")
    class GrossMarginTest {

        @Test
        @DisplayName("grossMargin<0 加 0.15 分")
        void shouldAdd015WhenGrossMarginNegative() {
            Map<String, Object> p = params();
            p.put("grossMargin", -0.10);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.1500"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("毛利率为负"));
        }

        @Test
        @DisplayName("grossMargin>=0 不加分")
        void shouldNotAddScoreWhenGrossMarginNonNegative() {
            Map<String, Object> p = params();
            p.put("grossMargin", 0.10);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
        }
    }

    // ==================== 高风险事件数评分测试 ====================

    @Nested
    @DisplayName("高风险事件数评分测试")
    class HighRiskCountTest {

        @Test
        @DisplayName("highRiskCount>=2 加 0.10 分")
        void shouldAdd010WhenHighRiskCountAbove2() {
            Map<String, Object> p = params();
            p.put("highRiskCount", 2);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.1000"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("高风险事件≥2"));
        }

        @Test
        @DisplayName("highRiskCount>=1 加 0.05 分")
        void shouldAdd005WhenHighRiskCount1() {
            Map<String, Object> p = params();
            p.put("highRiskCount", 1);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0500"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("存在高风险事件"));
        }

        @Test
        @DisplayName("highRiskCount=0 不加分")
        void shouldNotAddScoreWhenHighRiskCountZero() {
            Map<String, Object> p = params();
            p.put("highRiskCount", 0);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
        }
    }

    // ==================== 风险事件数评分测试 ====================

    @Nested
    @DisplayName("风险事件数评分测试")
    class RiskCountTest {

        @Test
        @DisplayName("riskCount>=5 加 0.05 分")
        void shouldAdd005WhenRiskCountAbove5() {
            Map<String, Object> p = params();
            p.put("riskCount", 5);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0500"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("风险事件数≥5"));
        }

        @Test
        @DisplayName("riskCount<5 不加分")
        void shouldNotAddScoreWhenRiskCountBelow5() {
            Map<String, Object> p = params();
            p.put("riskCount", 4);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
        }
    }

    // ==================== 告警等级测试 ====================

    @Nested
    @DisplayName("告警等级测试")
    class AlertLevelTest {

        @Test
        @DisplayName("score>=0.55 → RED")
        void shouldReturnRedWhenScoreAbove55() {
            // cpi<0.85(0.35) + spi<0.85(0.20) + costOverrun>=0.20(0.20) = 0.75 → RED
            Map<String, Object> p = params();
            p.put("cpi", 0.80);
            p.put("spi", 0.80);
            p.put("costOverrun", 0.25);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
            assertThat(r.getScore().doubleValue()).isGreaterThanOrEqualTo(0.55);
        }

        @Test
        @DisplayName("score>=0.25 且 <0.55 → YELLOW")
        void shouldReturnYellowWhenScoreBetween25And55() {
            // cpi<0.85(0.35) = 0.35 → YELLOW
            Map<String, Object> p = params();
            p.put("cpi", 0.80);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getScore().doubleValue()).isGreaterThanOrEqualTo(0.25);
            assertThat(r.getScore().doubleValue()).isLessThan(0.55);
        }

        @Test
        @DisplayName("score<0.25 → NORMAL")
        void shouldReturnNormalWhenScoreBelow25() {
            // spi<0.95(0.10) = 0.10 → NORMAL
            Map<String, Object> p = params();
            p.put("spi", 0.90);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
            assertThat(r.getScore().doubleValue()).isLessThan(0.25);
        }

        @Test
        @DisplayName("全部正常 - score=0 → NORMAL")
        void shouldReturnNormalWhenAllMetricsHealthy() {
            Map<String, Object> p = params();
            p.put("cpi", 1.0);
            p.put("spi", 1.0);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
            assertThat(r.getMatchedRules()).isEmpty();
            assertThat(r.getSuggestion()).contains("正常");
        }
    }

    // ==================== 置信度测试 ====================

    @Nested
    @DisplayName("置信度测试")
    class ConfidenceTest {

        @Test
        @DisplayName("confidence = 0.7 + min(0.25, matched.size()*0.05)")
        void shouldCalculateConfidenceCorrectly() {
            // 1 个匹配规则 → 0.7 + 0.05 = 0.75
            Map<String, Object> p = params();
            p.put("cpi", 0.80);  // 1 个规则
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getConfidence()).isEqualByComparingTo(new BigDecimal("0.7500"));
        }

        @Test
        @DisplayName("matched.size()=5 时 confidence=0.95（0.7+0.25）")
        void shouldCapConfidenceAt095() {
            // 触发 5 个规则：cpi<0.85 + spi<0.85 + costOverrun>=0.20 + grossMargin<0 + highRiskCount>=2
            Map<String, Object> p = params();
            p.put("cpi", 0.80);
            p.put("spi", 0.80);
            p.put("costOverrun", 0.25);
            p.put("grossMargin", -0.10);
            p.put("highRiskCount", 2);
            AgentResult r = agent.execute(ctx(p));

            // 5 个规则 → 0.7 + min(0.25, 5*0.05) = 0.7 + 0.25 = 0.95
            assertThat(r.getMatchedRules()).hasSize(5);
            assertThat(r.getConfidence()).isEqualByComparingTo(new BigDecimal("0.9500"));
        }

        @Test
        @DisplayName("matched.size()=6 时 confidence 仍为 0.95（cap 生效）")
        void shouldCapConfidenceWhenMatchedExceeds5() {
            // 触发 6 个规则：5 + riskCount>=5
            Map<String, Object> p = params();
            p.put("cpi", 0.80);
            p.put("spi", 0.80);
            p.put("costOverrun", 0.25);
            p.put("grossMargin", -0.10);
            p.put("highRiskCount", 2);
            p.put("riskCount", 5);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getMatchedRules()).hasSize(6);
            // 0.7 + min(0.25, 6*0.05) = 0.7 + 0.25 = 0.95（cap 生效）
            assertThat(r.getConfidence()).isEqualByComparingTo(new BigDecimal("0.9500"));
        }
    }

    // ==================== 空入参测试 ====================

    @Nested
    @DisplayName("空入参测试")
    class EmptyInputTest {

        @Test
        @DisplayName("params=null 时使用默认值不抛错")
        void shouldUseDefaultsWhenParamsNull() {
            AgentContext ctx = new AgentContext();
            ctx.setParams(null);
            AgentResult r = agent.execute(ctx);

            // 全部使用默认值（cpi=1, spi=1, grossMargin=0, progressPct=0, costOverrun=0, riskCount=0, highRiskCount=0）
            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
        }

        @Test
        @DisplayName("params=空 Map 时使用默认值")
        void shouldUseDefaultsWhenParamsEmpty() {
            AgentResult r = agent.execute(ctx(new HashMap<>()));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
        }

        @Test
        @DisplayName("params 中 String 类型数字能被 toBd 转换")
        void shouldConvertStringNumberToBigDecimal() {
            Map<String, Object> p = params();
            p.put("cpi", "0.80");  // String 类型
            AgentResult r = agent.execute(ctx(p));

            // String "0.80" 应被 toBd 转换为 BigDecimal 0.80
            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.3500"));
            assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("CPI<0.85"));
        }

        @Test
        @DisplayName("params 中 BigDecimal 类型直接使用")
        void shouldHandleBigDecimalType() {
            Map<String, Object> p = params();
            p.put("cpi", new BigDecimal("0.80"));
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.3500"));
        }

        @Test
        @DisplayName("params 中 Double 类型转换")
        void shouldHandleDoubleType() {
            Map<String, Object> p = params();
            p.put("cpi", 0.80);  // Double 类型
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.3500"));
        }

        @Test
        @DisplayName("params 中非法字符串转换失败时使用默认值")
        void shouldUseDefaultWhenStringConversionFails() {
            Map<String, Object> p = params();
            p.put("cpi", "not-a-number");  // 非法字符串
            AgentResult r = agent.execute(ctx(p));

            // toBd 转换失败，使用默认值 BigDecimal.ONE → cpi=1.0 → 不加分
            assertThat(r.getScore()).isEqualByComparingTo(new BigDecimal("0.0000"));
        }
    }

    // ==================== payload 测试 ====================

    @Nested
    @DisplayName("payload 测试")
    class PayloadTest {

        @Test
        @DisplayName("payload 包含原始 score / cpi / spi / progressPct / grossMargin")
        void shouldPopulatePayload() {
            Map<String, Object> p = params();
            p.put("cpi", 0.80);
            p.put("spi", 0.90);
            p.put("grossMargin", -0.10);
            p.put("progressPct", 0.50);
            AgentResult r = agent.execute(ctx(p));

            assertThat(r.getPayload()).isNotNull();
            assertThat(r.getPayload().get("cpi")).isNotNull();
            assertThat(r.getPayload().get("spi")).isNotNull();
            assertThat(r.getPayload().get("grossMargin")).isNotNull();
            assertThat(r.getPayload().get("progressPct")).isNotNull();
            assertThat(r.getPayload().get("score")).isNotNull();
        }
    }
}
