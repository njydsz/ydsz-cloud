package com.njydsz.pmis.literule.impl;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模板渲染升级测试
 *
 * <p>验证 ${var} 变量替换、${expression} Aviator 表达式、${value | format} 格式化。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("ExpressionRule 模板渲染升级测试")
class TemplateRenderingTest {

    private AviatorExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator();
    }

    @Test
    @DisplayName("${var} 简单变量替换（向后兼容）")
    void shouldRenderSimpleVariable() {
        RuleDefinition def = RuleDefinition.builder()
                .code("TPL_VAR")
                .name("变量替换")
                .category("TEST")
                .conditionExpression("amount > 0")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate("金额: ${amount}")
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", new BigDecimal("12345.67"));
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals("金额: 12345.67", result.getTitle());
    }

    @Test
    @DisplayName("${expression} Aviator 表达式求值")
    void shouldRenderAviatorExpression() {
        RuleDefinition def = RuleDefinition.builder()
                .code("TPL_EXPR")
                .name("表达式渲染")
                .category("TEST")
                .conditionExpression("amount > 0")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate("税额: ${amount * 0.1}")
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", new BigDecimal("1000"));
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals("税额: 100", result.getTitle());
    }

    @Test
    @DisplayName("${value | #,##0.00} DecimalFormat 格式化")
    void shouldRenderWithDecimalFormat() {
        RuleDefinition def = RuleDefinition.builder()
                .code("TPL_FMT")
                .name("格式化渲染")
                .category("TEST")
                .conditionExpression("amount > 0")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate("金额: ${amount | #,##0.00}")
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", new BigDecimal("1234567.891"));
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals("金额: 1,234,567.89", result.getTitle());
    }

    @Test
    @DisplayName("${value | %.2f} printf 风格格式化")
    void shouldRenderWithPrintfFormat() {
        RuleDefinition def = RuleDefinition.builder()
                .code("TPL_PRINTF")
                .name("printf格式化")
                .category("TEST")
                .conditionExpression("ratio > 0")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate("比率: ${ratio | %.2f%%}")
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        Map<String, Object> facts = new HashMap<>();
        facts.put("ratio", new BigDecimal("0.856"));
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals("比率: 0.86%", result.getTitle());
    }

    @Test
    @DisplayName("模板中混合多个表达式")
    void shouldRenderMultipleExpressions() {
        RuleDefinition def = RuleDefinition.builder()
                .code("TPL_MULTI")
                .name("多表达式")
                .category("TEST")
                .conditionExpression("amount > 0")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate("原价: ${amount}, 税额: ${amount * 0.1}, 总计: ${amount * 1.1 | #,##0.00}")
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", new BigDecimal("1000"));
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals("原价: 1000, 税额: 100, 总计: 1,100.00", result.getTitle());
    }

    @Test
    @DisplayName("变量不存在时渲染为空字符串")
    void shouldRenderEmptyForMissingVar() {
        RuleDefinition def = RuleDefinition.builder()
                .code("TPL_MISSING")
                .name("缺值渲染")
                .category("TEST")
                .conditionExpression("amount > 0")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate("值: ${nonexistent}")
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", new BigDecimal("100"));
        RuleResult result = rule.evaluate(RuleContext.of(facts));

        assertTrue(result.isTriggered());
        assertEquals("值: ", result.getTitle());
    }
}
