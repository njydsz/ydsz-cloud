package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.DecisionTreeDefinition;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.ScorecardDefinition;
import com.njydsz.pmis.literule.api.ScriptDefinition;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评分卡 / 决策树 / 脚本规则 from Definition 工厂方法测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class RuleFromDefinitionTest {

    private final ExpressionEvaluator evaluator = new AviatorExpressionEvaluator(false);

    @Test
    void scorecardFromDefinitionShouldEvaluateCorrectly() {
        ScorecardDefinition def = ScorecardDefinition.builder()
                .ruleCode("CREDIT_SCORE")
                .ruleName("客户信用评分")
                .category("RISK")
                .baseScore(100)
                .redThreshold(60)
                .yellowThreshold(80)
                .factors(List.of(
                        ScorecardDefinition.ScoreFactor.builder()
                                .conditionExpression("overdueCount > 3").score(-30).description("逾期次数过多").build(),
                        ScorecardDefinition.ScoreFactor.builder()
                                .conditionExpression("paymentRatio < 0.5").score(-20).description("付款比率过低").build()
                ))
                .build();

        ScorecardRule rule = ScorecardRule.from(def, evaluator);

        Map<String, Object> facts = new HashMap<>();
        facts.put("overdueCount", 5);
        facts.put("paymentRatio", 0.3);
        RuleContext ctx = RuleContext.of(facts);

        RuleResult result = rule.evaluate(ctx);
        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.RED, result.getSeverity());
        assertEquals(50.0, Double.parseDouble(result.getCurrentValue()), 0.001); // 100 - 30 - 20 = 50
    }

    @Test
    void decisionTreeFromDefinitionShouldEvaluateCorrectly() {
        DecisionTreeDefinition def = DecisionTreeDefinition.builder()
                .ruleCode("RISK_LEVEL")
                .ruleName("项目风险分级")
                .category("RISK")
                .root(DecisionTreeDefinition.DecisionNode.builder()
                        .conditionExpression("budgetUsedRatio > 0.9")
                        .leaf(false)
                        .trueBranch(DecisionTreeDefinition.DecisionNode.builder()
                                .leaf(true)
                                .severity("RED")
                                .title("严重超支")
                                .description("预算使用率超过 90%")
                                .build())
                        .falseBranch(DecisionTreeDefinition.DecisionNode.builder()
                                .conditionExpression("budgetUsedRatio > 0.7")
                                .leaf(false)
                                .trueBranch(DecisionTreeDefinition.DecisionNode.builder()
                                        .leaf(true)
                                        .severity("YELLOW")
                                        .title("中度超支")
                                        .description("预算使用率超过 70%")
                                        .build())
                                .falseBranch(DecisionTreeDefinition.DecisionNode.builder()
                                        .leaf(true)
                                        .severity("INFO")
                                        .title("正常")
                                        .description("预算使用正常")
                                        .build())
                                .build())
                        .build())
                .build();

        DecisionTreeRule rule = DecisionTreeRule.from(def, evaluator);

        Map<String, Object> facts = new HashMap<>();
        facts.put("budgetUsedRatio", 0.95);
        RuleResult redResult = rule.evaluate(RuleContext.of(facts));
        assertTrue(redResult.isTriggered());
        assertEquals(RuleSeverity.RED, redResult.getSeverity());
        assertEquals("严重超支", redResult.getTitle());

        facts.put("budgetUsedRatio", 0.75);
        RuleResult yellowResult = rule.evaluate(RuleContext.of(facts));
        assertEquals(RuleSeverity.YELLOW, yellowResult.getSeverity());

        facts.put("budgetUsedRatio", 0.5);
        RuleResult infoResult = rule.evaluate(RuleContext.of(facts));
        assertEquals(RuleSeverity.INFO, infoResult.getSeverity());
    }

    @Test
    void scriptFromDefinitionShouldEvaluateCorrectly() {
        ScriptDefinition def = ScriptDefinition.builder()
                .ruleCode("GROOVY_RULE")
                .ruleName("复合规则")
                .category("COMPLEX")
                .script("def budget = facts.budgetUsedRatio ?: 0\n" +
                        "def spi = facts.spi ?: 1.0\n" +
                        "if (budget >= 0.9 && spi < 0.85) {\n" +
                        "    severity = 'RED'\n" +
                        "    title = '预算超支且进度滞后'\n" +
                        "    description = '预算=' + budget + ', SPI=' + spi\n" +
                        "    return true\n" +
                        "}\n" +
                        "return false")
                .defaultSeverity("YELLOW")
                .sandboxEnabled(true)
                .build();

        ScriptRule rule = ScriptRule.from(def);

        Map<String, Object> facts = new HashMap<>();
        facts.put("budgetUsedRatio", 0.95);
        facts.put("spi", 0.8);
        RuleResult triggeredResult = rule.evaluate(RuleContext.of(facts));
        assertTrue(triggeredResult.isTriggered());
        assertEquals(RuleSeverity.RED, triggeredResult.getSeverity());

        facts.put("budgetUsedRatio", 0.5);
        facts.put("spi", 1.0);
        RuleResult notTriggeredResult = rule.evaluate(RuleContext.of(facts));
        assertFalse(notTriggeredResult.isTriggered());
    }

    @Test
    void scriptFromDefinitionShouldRejectDangerousApiInSandbox() {
        ScriptDefinition def = ScriptDefinition.builder()
                .ruleCode("DANGEROUS_RULE")
                .ruleName("危险脚本")
                .script("Runtime.getRuntime().exec('ls')")
                .sandboxEnabled(true)
                .build();

        // 沙箱模式下应抛出 SecurityException
        assertThrows(SecurityException.class, () -> ScriptRule.from(def));
    }
}
