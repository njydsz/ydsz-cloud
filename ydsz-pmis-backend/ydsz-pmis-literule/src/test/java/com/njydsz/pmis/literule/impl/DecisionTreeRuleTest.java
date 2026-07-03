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
 * 决策树规则测试
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("DecisionTreeRule 决策树规则测试")
class DecisionTreeRuleTest {

    private AviatorExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator();
    }

    @Test
    @DisplayName("三层决策树：budgetUsedRatio > 0.9 → RED, > 0.7 → YELLOW, else → INFO")
    void shouldTraverseThreeLevelTree() {
        DecisionTreeRule rule = new DecisionTreeRule(
                "RISK_LEVEL", "项目风险分级", "RISK", 100, null,
                DecisionTreeRule.DecisionNode.condition("budgetUsedRatio > 0.9",
                        DecisionTreeRule.DecisionNode.leaf(RuleSeverity.RED, "严重超支", "预算使用率超过90%"),
                        DecisionTreeRule.DecisionNode.condition("budgetUsedRatio > 0.7",
                                DecisionTreeRule.DecisionNode.leaf(RuleSeverity.YELLOW, "中度超支", "预算使用率超过70%"),
                                DecisionTreeRule.DecisionNode.leaf(RuleSeverity.INFO, "正常", "预算使用正常")
                        )
                ),
                evaluator
        );

        // 严重超支 95%
        Map<String, Object> facts1 = new HashMap<>();
        facts1.put("budgetUsedRatio", 0.95);
        RuleResult r1 = rule.evaluate(RuleContext.of(facts1));
        assertTrue(r1.isTriggered());
        assertEquals(RuleSeverity.RED, r1.getSeverity());
        assertEquals("严重超支", r1.getTitle());

        // 中度超支 75%
        Map<String, Object> facts2 = new HashMap<>();
        facts2.put("budgetUsedRatio", 0.75);
        RuleResult r2 = rule.evaluate(RuleContext.of(facts2));
        assertEquals(RuleSeverity.YELLOW, r2.getSeverity());

        // 正常 50%
        Map<String, Object> facts3 = new HashMap<>();
        facts3.put("budgetUsedRatio", 0.50);
        RuleResult r3 = rule.evaluate(RuleContext.of(facts3));
        assertEquals(RuleSeverity.INFO, r3.getSeverity());
    }

    @Test
    @DisplayName("条件求值失败走 false 分支")
    void shouldGoFalseBranchOnEvalError() {
        DecisionTreeRule rule = new DecisionTreeRule(
                "ERROR_TEST", "错误测试", "TEST", 100, null,
                DecisionTreeRule.DecisionNode.condition("nonexistent > 1",
                        DecisionTreeRule.DecisionNode.leaf(RuleSeverity.RED, "TRUE", ""),
                        DecisionTreeRule.DecisionNode.leaf(RuleSeverity.INFO, "FALSE", "")
                ),
                evaluator
        );

        RuleResult result = rule.evaluate(RuleContext.of(new HashMap<>()));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.INFO, result.getSeverity());
        assertEquals("FALSE", result.getTitle());
    }

    @Test
    @DisplayName("单层决策树（直接叶子节点）")
    void shouldHandleSingleLeafTree() {
        DecisionTreeRule rule = new DecisionTreeRule(
                "SIMPLE", "简单规则", "TEST", 100, null,
                DecisionTreeRule.DecisionNode.leaf(RuleSeverity.YELLOW, "直接结果", "描述"),
                evaluator
        );

        RuleResult result = rule.evaluate(RuleContext.of(new HashMap<>()));

        assertTrue(result.isTriggered());
        assertEquals(RuleSeverity.YELLOW, result.getSeverity());
        assertEquals("直接结果", result.getTitle());
    }
}
