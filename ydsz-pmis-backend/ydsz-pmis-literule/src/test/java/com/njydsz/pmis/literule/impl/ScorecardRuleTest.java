package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评分卡规则测试
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("ScorecardRule 评分卡规则测试")
class ScorecardRuleTest {

    private AviatorExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator();
    }

    @Test
    @DisplayName("多因子评分：基础分100 + 命中扣分 = 最终分数")
    void shouldCalculateScoreFromMultipleFactors() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CREDIT_SCORE")
                .name("客户信用评分")
                .category("RISK")
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -30, "逾期次数过多"))
                .factor(ScorecardRule.ScoreFactor.of("paymentRatio < 0.5", -20, "付款比率过低"))
                .factor(ScorecardRule.ScoreFactor.of("contractAmount > 1000000", 10, "大额合同加分"))
                .redThreshold(60)
                .yellowThreshold(80)
                .build();

        // 逾期+付款低+大额合同 = 100-30-20+10 = 60 → YELLOW（60 < 80）
        Map<String, Object> facts = new HashMap<>();
        facts.put("overdueCount", 5);
        facts.put("paymentRatio", 0.3);
        facts.put("contractAmount", 2000000);
        RuleContext context = RuleContext.of(facts);

        RuleResult result = rule.evaluate(context);

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.YELLOW, result.getSeverity());
        assertTrue(result.getTitle().contains("60"));
        assertTrue(result.getDescription().contains("逾期次数过多"));
    }

    @Test
    @DisplayName("高分（无风险因子）→ INFO")
    void shouldReturnInfoForHighScore() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("SAFE_SCORE")
                .name("安全评分")
                .category("RISK")
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -30, "逾期"))
                .redThreshold(60)
                .yellowThreshold(80)
                .build();

        Map<String, Object> facts = new HashMap<>();
        facts.put("overdueCount", 0); // 不命中
        RuleContext context = RuleContext.of(facts);

        RuleResult result = rule.evaluate(context);

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.INFO, result.getSeverity());
        assertTrue(result.getTitle().contains("100"));
    }

    @Test
    @DisplayName("低分（高风险）→ RED")
    void shouldReturnRedForLowScore() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("HIGH_RISK")
                .name("高风险评分")
                .category("RISK")
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("overdueCount > 3", -50, "严重逾期"))
                .redThreshold(60)
                .yellowThreshold(80)
                .build();

        Map<String, Object> facts = new HashMap<>();
        facts.put("overdueCount", 10);
        RuleContext context = RuleContext.of(facts);

        RuleResult result = rule.evaluate(context);

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.RED, result.getSeverity());
        // 100-50=50, 钳制后50
        assertTrue(result.getTitle().contains("50"));
    }

    @Test
    @DisplayName("分数钳制到 0-100 范围")
    void shouldClampScoreToRange() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("CLAMP_TEST")
                .name("钳制测试")
                .category("TEST")
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("true", -200, "超扣"))
                .redThreshold(60)
                .yellowThreshold(80)
                .build();

        RuleContext context = RuleContext.of(new HashMap<>());
        RuleResult result = rule.evaluate(context);

        // 100-200=-100 → 钳制到 0
        assertTrue(result.getTitle().contains("0"));
    }

    @Test
    @DisplayName("因子求值异常被隔离，不影响其他因子")
    void shouldIsolateFactorEvalError() {
        ScorecardRule rule = ScorecardRule.builder()
                .code("ISOLATE_TEST")
                .name("隔离测试")
                .category("TEST")
                .evaluator(evaluator)
                .factor(ScorecardRule.ScoreFactor.of("nonexistent.field > 1", -10, "异常因子"))
                .factor(ScorecardRule.ScoreFactor.of("true", -5, "正常扣分"))
                .redThreshold(60)
                .yellowThreshold(80)
                .build();

        RuleContext context = RuleContext.of(new HashMap<>());
        RuleResult result = rule.evaluate(context);

        // 异常因子被跳过，只扣正常因子: 100-5=95
        assertTrue(result.isTriggered());
        assertTrue(result.getTitle().contains("95"));
    }
}
