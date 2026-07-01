package com.njydsz.pmis.workflow.flow.engine.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultFlowVariableStrategy 单元测试
 *
 * <p>覆盖：${var} / ${var op value} 表达式解析与办理人角色解析。
 */
@DisplayName("DefaultFlowVariableStrategy 单元测试")
class DefaultFlowVariableStrategyTest {

    private final DefaultFlowVariableStrategy strategy = new DefaultFlowVariableStrategy();

    // ============== evaluate() 表达式求值 ==============

    @Test
    @DisplayName("空条件应返回 true（无跳转限制）")
    void evaluate_empty_returnsTrue() {
        assertThat(strategy.evaluate(null, Map.of())).isTrue();
        assertThat(strategy.evaluate("", Map.of())).isTrue();
        assertThat(strategy.evaluate("  ", Map.of())).isTrue();
    }

    @Test
    @DisplayName("${var} 非空判断")
    void evaluate_variableExists() {
        Map<String, Object> v = new HashMap<>();
        v.put("amount", 1000);
        assertThat(strategy.evaluate("${amount}", v)).isTrue();
        assertThat(strategy.evaluate("${unknown}", v)).isFalse();
    }

    @Test
    @DisplayName("${var op value} 数值比较")
    void evaluate_numericComparison() {
        Map<String, Object> v = Map.of("amount", 200000);
        assertThat(strategy.evaluate("${amount > 100000}", v)).isTrue();
        assertThat(strategy.evaluate("${amount > 1000000}", v)).isFalse();
        assertThat(strategy.evaluate("${amount == 200000}", v)).isTrue();
        assertThat(strategy.evaluate("${amount != 200000}", v)).isFalse();
        assertThat(strategy.evaluate("${amount <= 200000}", v)).isTrue();
        assertThat(strategy.evaluate("${amount >= 100000}", v)).isTrue();
    }

    @Test
    @DisplayName("${var op value} 字符串比较")
    void evaluate_stringComparison() {
        Map<String, Object> v = Map.of("type", "VIP");
        assertThat(strategy.evaluate("${type == 'VIP'}", v)).isTrue();
        assertThat(strategy.evaluate("${type == 'NORMAL'}", v)).isFalse();
    }

    @Test
    @DisplayName("非法表达式返回 false，不抛异常")
    void evaluate_invalidExpression_returnsFalse() {
        Map<String, Object> v = Map.of("a", 1);
        assertThat(strategy.evaluate("${a >>> 1}", v)).isFalse();
        assertThat(strategy.evaluate("@#$%", v)).isFalse();
    }

    // ============== resolveAssignee() 角色解析 ==============

    @Test
    @DisplayName("role: 前缀解析为 ROLE")
    void resolveAssignee_role() {
        String result = strategy.resolveAssignee("role:hr", Map.of());
        assertThat(result).isEqualTo("role:hr");
    }

    @Test
    @DisplayName("dept: 前缀解析为 DEPT")
    void resolveAssignee_dept() {
        String result = strategy.resolveAssignee("dept:1001", Map.of());
        assertThat(result).isEqualTo("dept:1001");
    }

    @Test
    @DisplayName("user: 前缀解析为 USER")
    void resolveAssignee_user() {
        String result = strategy.resolveAssignee("user:1001", Map.of());
        assertThat(result).isEqualTo("user:1001");
    }

    @Test
    @DisplayName("${expression} 前缀解析为 SPEL")
    void resolveAssignee_spel() {
        String result = strategy.resolveAssignee("${initiatorId}", Map.of());
        assertThat(result).isEqualTo("${initiatorId}");
    }

    @Test
    @DisplayName("空字符串返回 null")
    void resolveAssignee_empty_returnsNull() {
        assertThat(strategy.resolveAssignee(null, Map.of())).isNull();
        assertThat(strategy.resolveAssignee("", Map.of())).isNull();
    }
}
