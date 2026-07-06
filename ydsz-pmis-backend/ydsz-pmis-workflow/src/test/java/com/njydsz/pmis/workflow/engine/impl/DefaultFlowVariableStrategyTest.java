package com.njydsz.pmis.workflow.engine.impl;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * DefaultFlowVariableStrategy 单元测试
 *
 * <p>GAP-P1-8: 补强流程变量表达式策略的测试覆盖。
 * 覆盖 Aviator 优先路径 + 传统正则回退路径，以及 7 种向后兼容语法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DefaultFlowVariableStrategyTest {

    @Mock private ObjectProvider<ExpressionEvaluator> evaluatorProvider;
    @Mock private ExpressionEvaluator evaluator;

    /** 无 Aviator 的策略（测试传统正则解析器） */
    private DefaultFlowVariableStrategy legacyStrategy;
    /** 有 Aviator 的策略（测试 Aviator 优先路径） */
    private DefaultFlowVariableStrategy aviatorStrategy;

    @BeforeEach
    void setUp() {
        // legacy：ObjectProvider 返回 null（Aviator 不可用）
        when(evaluatorProvider.getIfAvailable()).thenReturn(null);
        legacyStrategy = new DefaultFlowVariableStrategy(evaluatorProvider);

        // aviator：ObjectProvider 返回 mock
        org.mockito.Mockito.reset(evaluatorProvider);
        when(evaluatorProvider.getIfAvailable()).thenReturn(evaluator);
        aviatorStrategy = new DefaultFlowVariableStrategy(evaluatorProvider);
    }

    // ============ evaluate - 空值与边界 ============

    @Test
    @DisplayName("evaluate - null 条件返回 true")
    void evaluateShouldReturnTrueWhenNull() {
        assertThat(legacyStrategy.evaluate(null, Map.of())).isTrue();
    }

    @Test
    @DisplayName("evaluate - 空白条件返回 true")
    void evaluateShouldReturnTrueWhenBlank() {
        assertThat(legacyStrategy.evaluate("   ", Map.of())).isTrue();
    }

    @Test
    @DisplayName("evaluate - dmn: 前缀返回 false 并告警")
    void evaluateShouldReturnFalseWhenDmnPrefix() {
        assertThat(legacyStrategy.evaluate("dmn:approval_table", Map.of())).isFalse();
    }

    // ============ evaluate - 传统正则路径（无 Aviator）============

    @Test
    @DisplayName("evaluate - ${var} 简单占位符（变量存在且非 false 返回 true）")
    void legacyEvaluatePlaceholderExists() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("flag", true);
        assertThat(legacyStrategy.evaluate("${flag}", vars)).isTrue();
    }

    @Test
    @DisplayName("evaluate - ${var} 变量不存在返回 false")
    void legacyEvaluatePlaceholderMissing() {
        assertThat(legacyStrategy.evaluate("${missing}", Map.of())).isFalse();
    }

    @Test
    @DisplayName("evaluate - ${amount > 100} 比较表达式（成立）")
    void legacyEvaluateCompareGt() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", 200);
        assertThat(legacyStrategy.evaluate("${amount > 100}", vars)).isTrue();
    }

    @Test
    @DisplayName("evaluate - ${amount > 100} 比较表达式（不成立）")
    void legacyEvaluateCompareGtFalse() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", 50);
        assertThat(legacyStrategy.evaluate("${amount > 100}", vars)).isFalse();
    }

    @Test
    @DisplayName("evaluate - ${a > 100} && ${b < 50} 逻辑与（全真为真）")
    void legacyEvaluateAnd() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 200);
        vars.put("b", 30);
        assertThat(legacyStrategy.evaluate("${a > 100} && ${b < 50}", vars)).isTrue();
    }

    @Test
    @DisplayName("evaluate - ${a > 100} && ${b < 50} 逻辑与（一假为假）")
    void legacyEvaluateAndOneFalse() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 200);
        vars.put("b", 60);
        assertThat(legacyStrategy.evaluate("${a > 100} && ${b < 50}", vars)).isFalse();
    }

    @Test
    @DisplayName("evaluate - ${a > 100} || ${b < 50} 逻辑或（一真为真）")
    void legacyEvaluateOr() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 50);
        vars.put("b", 30);
        assertThat(legacyStrategy.evaluate("${a > 100} || ${b < 50}", vars)).isTrue();
    }

    @Test
    @DisplayName("evaluate - !${flag} 逻辑非")
    void legacyEvaluateNot() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("flag", true);
        assertThat(legacyStrategy.evaluate("!${flag}", vars)).isFalse();
    }

    @Test
    @DisplayName("evaluate - 裸变量比较 amount > 100（无 ${} 包裹）")
    void legacyEvaluateBareCompare() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", 200);
        assertThat(legacyStrategy.evaluate("amount > 100", vars)).isTrue();
    }

    @Test
    @DisplayName("evaluate - 布尔字面量 true")
    void legacyEvaluateBooleanTrue() {
        assertThat(legacyStrategy.evaluate("true", Map.of())).isTrue();
    }

    @Test
    @DisplayName("evaluate - 布尔字面量 false")
    void legacyEvaluateBooleanFalse() {
        assertThat(legacyStrategy.evaluate("false", Map.of())).isFalse();
    }

    @Test
    @DisplayName("evaluate - 无法识别的表达式返回 false")
    void legacyEvaluateUnrecognized() {
        assertThat(legacyStrategy.evaluate("@#$%", Map.of())).isFalse();
    }

    // ============ evaluate - Aviator 优先路径 ============

    @Test
    @DisplayName("evaluate - Aviator 可用时优先委托求值")
    void aviatorEvaluateShouldDelegate() {
        when(evaluator.evalBoolean(anyString(), any(RuleContext.class))).thenReturn(true);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", 200);
        assertThat(aviatorStrategy.evaluate("amount > 100", vars)).isTrue();
    }

    @Test
    @DisplayName("evaluate - Aviator 求值失败时回退到正则解析")
    void aviatorEvaluateShouldFallbackOnFailure() {
        when(evaluator.evalBoolean(anyString(), any(RuleContext.class)))
                .thenThrow(new RuntimeException("Aviator 语法错误"));
        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", 200);
        // Aviator 失败后回退到正则，应能正确解析 ${amount > 100}
        assertThat(aviatorStrategy.evaluate("${amount > 100}", vars)).isTrue();
    }

    @Test
    @DisplayName("evaluate - ${} 包裹被剥离后传给 Aviator")
    void aviatorEvaluateShouldStripPlaceholders() {
        when(evaluator.evalBoolean(anyString(), any(RuleContext.class))).thenReturn(true);
        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 200);
        vars.put("b", 30);
        aviatorStrategy.evaluate("${a > 100} && ${b < 50}", vars);
        // 验证传给 Aviator 的表达式已剥离 ${}
        org.mockito.Mockito.verify(evaluator).evalBoolean(
                org.mockito.ArgumentMatchers.eq("a > 100 && b < 50"),
                any(RuleContext.class));
    }

    // ============ resolveAssignee - 传统路径 ============

    @Test
    @DisplayName("resolveAssignee - null 表达式返回 null")
    void resolveAssigneeNullReturnsNull() {
        assertThat(legacyStrategy.resolveAssignee(null, Map.of())).isNull();
    }

    @Test
    @DisplayName("resolveAssignee - 空白表达式返回 null")
    void resolveAssigneeBlankReturnsNull() {
        assertThat(legacyStrategy.resolveAssignee("  ", Map.of())).isNull();
    }

    @Test
    @DisplayName("resolveAssignee - ${var} 占位符替换")
    void legacyResolveAssigneePlaceholder() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("userId", "1001");
        assertThat(legacyStrategy.resolveAssignee("${userId}", vars)).isEqualTo("1001");
    }

    @Test
    @DisplayName("resolveAssignee - 固定字符串 user:1001 原样返回")
    void legacyResolveAssigneeLiteral() {
        assertThat(legacyStrategy.resolveAssignee("user:1001", Map.of())).isEqualTo("user:1001");
    }

    @Test
    @DisplayName("resolveAssignee - 三元运算符 ${cond ? 'A' : 'B'}（cond 为 true）")
    void legacyResolveAssigneeTernaryTrue() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("cond", true);
        // 注意：传统正则解析器对裸变量名 cond 无法解析为布尔值，
        // 需使用 ${cond} 形式的条件表达式
        assertThat(legacyStrategy.resolveAssignee("${${cond} ? 'A' : 'B'}", vars)).isEqualTo("A");
    }

    @Test
    @DisplayName("resolveAssignee - 三元运算符 ${cond ? 'A' : 'B'}（cond 为 false）")
    void legacyResolveAssigneeTernaryFalse() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("cond", false);
        assertThat(legacyStrategy.resolveAssignee("${${cond} ? 'A' : 'B'}", vars)).isEqualTo("B");
    }

    // ============ resolveAssignee - Aviator 路径 ============

    @Test
    @DisplayName("resolveAssignee - Aviator 可用时对 ${} 表达式优先委托")
    void aviatorResolveAssigneeShouldDelegate() {
        when(evaluator.eval(anyString(), any(RuleContext.class))).thenReturn("resolved_user");
        Map<String, Object> vars = new HashMap<>();
        vars.put("userId", "1001");
        assertThat(aviatorStrategy.resolveAssignee("${userId}", vars)).isEqualTo("resolved_user");
    }

    @Test
    @DisplayName("resolveAssignee - 非 ${} 表达式不委托 Aviator，走传统解析")
    void aviatorResolveAssigneeShouldNotDelegateNonPlaceholder() {
        // 固定字符串不经过 Aviator
        assertThat(aviatorStrategy.resolveAssignee("user:1001", Map.of())).isEqualTo("user:1001");
        org.mockito.Mockito.verify(evaluator, org.mockito.Mockito.never())
                .eval(anyString(), any(RuleContext.class));
    }
}
