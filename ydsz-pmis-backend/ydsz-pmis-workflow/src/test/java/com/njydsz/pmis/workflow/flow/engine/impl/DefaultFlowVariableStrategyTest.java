package com.njydsz.pmis.workflow.flow.engine.impl;

import com.njydsz.pmis.workflow.engine.impl.DefaultFlowVariableStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultFlowVariableStrategy 单元测试
 *
 * <p>覆盖：${var} / ${var op value} 表达式解析与办理人角色解析。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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

    // ============== P2-14: 逻辑组合表达式 ==============

    @Test
    @DisplayName("&& 逻辑与：两边均为 true 返回 true")
    void evaluate_and_bothTrue() {
        Map<String, Object> v = Map.of("amount", 200, "level", 5);
        assertThat(strategy.evaluate("${amount > 100} && ${level > 3}", v)).isTrue();
    }

    @Test
    @DisplayName("&& 逻辑与：任一为 false 返回 false")
    void evaluate_and_oneFalse() {
        Map<String, Object> v = Map.of("amount", 50, "level", 5);
        assertThat(strategy.evaluate("${amount > 100} && ${level > 3}", v)).isFalse();
    }

    @Test
    @DisplayName("|| 逻辑或：任一为 true 返回 true")
    void evaluate_or_oneTrue() {
        Map<String, Object> v = Map.of("amount", 50, "level", 5);
        assertThat(strategy.evaluate("${amount > 100} || ${level > 3}", v)).isTrue();
    }

    @Test
    @DisplayName("|| 逻辑或：两边均为 false 返回 false")
    void evaluate_or_bothFalse() {
        Map<String, Object> v = Map.of("amount", 50, "level", 1);
        assertThat(strategy.evaluate("${amount > 100} || ${level > 3}", v)).isFalse();
    }

    @Test
    @DisplayName("&& 与 || 混合：优先级 && 高于 ||")
    void evaluate_mixedAndOr() {
        // (false && true) || true => true
        Map<String, Object> v = Map.of("a", 1, "b", 10, "c", 100);
        assertThat(strategy.evaluate("${a > 5} && ${b > 5} || ${c > 5}", v)).isTrue();
        // (true && false) || false => false
        Map<String, Object> v2 = Map.of("a", 10, "b", 1, "c", 1);
        assertThat(strategy.evaluate("${a > 5} && ${b > 5} || ${c > 5}", v2)).isFalse();
    }

    @Test
    @DisplayName("! 逻辑非：取反布尔变量")
    void evaluate_not_boolean() {
        Map<String, Object> v = Map.of("flag", true, "skip", false);
        assertThat(strategy.evaluate("!${flag}", v)).isFalse();
        assertThat(strategy.evaluate("!${skip}", v)).isTrue();
    }

    @Test
    @DisplayName("! 逻辑非：取反比较表达式")
    void evaluate_not_comparison() {
        Map<String, Object> v = Map.of("amount", 50);
        assertThat(strategy.evaluate("!${amount > 100}", v)).isTrue();
        assertThat(strategy.evaluate("!${amount < 100}", v)).isFalse();
    }

    @Test
    @DisplayName("!! 双重取反：还原原值")
    void evaluate_doubleNot() {
        Map<String, Object> v = Map.of("flag", true);
        assertThat(strategy.evaluate("!!${flag}", v)).isTrue();
    }

    @Test
    @DisplayName("! 与 && 组合：!flag && amount > 100")
    void evaluate_notAndCombination() {
        Map<String, Object> v = Map.of("flag", false, "amount", 200);
        assertThat(strategy.evaluate("!${flag} && ${amount > 100}", v)).isTrue();
    }

    @Test
    @DisplayName("字符串字面量内的 || 不被分割")
    void evaluate_stringLiteralContainsOr() {
        // 字符串 'a || b' 内的 || 不应被识别为逻辑或
        Map<String, Object> v = Map.of("type", "a || b");
        assertThat(strategy.evaluate("${type == 'a || b'}", v)).isTrue();
    }

    // ============== P2-14: 三元运算符 ==============

    @Test
    @DisplayName("三元运算符：条件为 true 返回 trueVal（字符串字面量）")
    void resolveAssignee_ternary_trueBranch() {
        Map<String, Object> v = Map.of("amount", 200);
        String result = strategy.resolveAssignee("${amount > 100 ? 'leader' : 'manager'}", v);
        assertThat(result).isEqualTo("leader");
    }

    @Test
    @DisplayName("三元运算符：条件为 false 返回 falseVal（字符串字面量）")
    void resolveAssignee_ternary_falseBranch() {
        Map<String, Object> v = Map.of("amount", 50);
        String result = strategy.resolveAssignee("${amount > 100 ? 'leader' : 'manager'}", v);
        assertThat(result).isEqualTo("manager");
    }

    @Test
    @DisplayName("三元运算符：分支为 ${var} 引用")
    void resolveAssignee_ternary_variableBranch() {
        Map<String, Object> v = new HashMap<>();
        v.put("amount", 200);
        v.put("leaderId", "user:999");
        v.put("managerId", "user:888");
        String result = strategy.resolveAssignee("${amount > 100 ? ${leaderId} : ${managerId}}", v);
        assertThat(result).isEqualTo("user:999");
    }

    @Test
    @DisplayName("三元运算符：分支为裸标识符")
    void resolveAssignee_ternary_bareIdentifier() {
        Map<String, Object> v = Map.of("amount", 50);
        String result = strategy.resolveAssignee("${amount > 100 ? leader : manager}", v);
        assertThat(result).isEqualTo("manager");
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
