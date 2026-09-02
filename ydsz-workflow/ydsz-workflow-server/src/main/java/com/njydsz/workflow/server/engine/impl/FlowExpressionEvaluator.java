package com.njydsz.workflow.server.engine.impl;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 条件表达式求值器（正则解析路径）
 *
 * <p>实现基于正则的条件表达式解析，支持以下语法：
 *
 * <ul>
 *   <li>${var} - 简单占位符替换
 *   <li>${var > 100} - 简单比较表达式
 *   <li>${a > 100} && ${b < 50} - 逻辑与（P2-14）
 *   <li>${a > 100} || ${b < 50} - 逻辑或（P2-14）
 *   <li>!${flag} - 逻辑非（P2-14）
 *   <li>${cond ? 'A' : 'B'} - 三元运算符（P2-14）
 *   <li>裸比较：amount > 100（不要求 ${} 包裹）
 *   <li>布尔字面量：true / false
 * </ul>
 *
 * <p>本组件作为 Aviator 引擎不可用时的降级路径（legacy path），保留原有 ${} 语法兼容。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowExpressionEvaluator {

  /** 比较表达式正则捕获组：右侧操作数 */
  private static final int CMP_GROUP_RIGHT = 3;

  private final FlowVariableReplacer variableReplacer;

  /**
   * 构造注入变量替换器
   *
   *
   * @param variableReplacer 变量占位符替换器
   */
  public FlowExpressionEvaluator(FlowVariableReplacer variableReplacer) {
    this.variableReplacer = variableReplacer;
  }

  /**
   * 传统正则解析器（回退方案）
   *
   * <p>当 Aviator 不可用或求值失败时被调用，保持原有 ${} 语法兼容。
   *
   * @param condition 条件表达式
   * @param variables 流程变量
   * @return 评估结果
   */
  public boolean evaluateLegacy(String condition, Map<String, Object> variables) {
    String expr = condition.trim();
    try {
      return evaluateOr(expr, variables);
    } catch (Exception e) {
      log.error("[FlowExpressionEvaluator] 条件解析异常: expr={} err={}", condition, e.getMessage());
      return false;
    }
  }

  /**
   * 顶层 || 逻辑或：任一子表达式为 true 即为 true。例如：${a > 100} || ${b < 50}
   *
   * @param expr 条件表达式
   * @param variables 流程变量上下文
   * @return true 表示任意子表达式为 true
   */
  boolean evaluateOr(String expr, Map<String, Object> variables) {
    String[] parts = FlowExpressionUtils.splitTopLevel(expr, "||");
    for (String part : parts) {
      if (evaluateAnd(part.trim(), variables)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 顶层 && 逻辑与：所有子表达式为 true 才为 true。例如：${a > 100} && ${b < 50}
   *
   * @param expr 条件表达式
   * @param variables 流程变量上下文
   * @return true 表示所有子表达式为 true
   */
  boolean evaluateAnd(String expr, Map<String, Object> variables) {
    String[] parts = FlowExpressionUtils.splitTopLevel(expr, "&&");
    for (String part : parts) {
      if (!evaluateNot(part.trim(), variables)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 逻辑非：!expr 形式，支持嵌套（如 !!flag）。
   *
   * @param expr 条件表达式（可含 ! 前缀）
   * @param variables 流程变量上下文
   * @return true 表示子表达式求值结果为 false
   */
  boolean evaluateNot(String expr, Map<String, Object> variables) {
    String trimmed = expr.trim();
    if (trimmed.startsWith("!")) {
      return !evaluateNot(trimmed.substring(1).trim(), variables);
    }
    return evaluateSingle(trimmed, variables);
  }

  /**
   * 单一原子表达式求值
   *
   * <ul>
   *   <li>${var op value} 比较表达式
   *   <li>${var} 非空判断
   *   <li>true/false 字面量
   *   <li>裸变量比较（如 "amount > 100"，P2-14）
   * </ul>
   *
   * @param expr 原子表达式
   * @param variables 流程变量
   * @return 评估结果
   */
  boolean evaluateSingle(String expr, Map<String, Object> variables) {
    if (expr.isEmpty()) {
      return true;
    }
    // 如果整个表达式是单一 ${...} 占位符（允许非标识符内容如 "${var op value}"）
    Matcher fullPh = Pattern.compile("^\\$\\{(.+)}\\s*$").matcher(expr);
    if (fullPh.matches()) {
      String inner = fullPh.group(1).trim();
      Matcher innerCmp = FlowExpressionUtils.COMPARE_INNER.matcher(inner);
      if (innerCmp.matches()) {
        String varName = innerCmp.group(1).trim();
        String op = innerCmp.group(2);
        String rawValue = innerCmp.group(CMP_GROUP_RIGHT).trim();
        Object actual = variableReplacer.lookupValue(varName, variables);
        Object expected = parseLiteral(rawValue);
        return compare(actual, op, expected);
      }
      // 单一 ${var}：非空 + 非 false 即视为 true
      Object v = variableReplacer.lookupValue(inner, variables);
      if (v == null) {
        return false;
      }
      if (v instanceof Boolean) {
        return (Boolean) v;
      }
      if (v instanceof String) {
        String s = ((String) v).trim();
        return !s.isEmpty() && !"false".equalsIgnoreCase(s);
      }
      return true;
    }
    // P2-14: 裸变量比较（如 "amount > 100"，不要求 ${} 包裹）
    Matcher bareCmp = FlowExpressionUtils.COMPARE_INNER.matcher(expr);
    if (bareCmp.matches()) {
      String varName = bareCmp.group(1).trim();
      String op = bareCmp.group(2);
      String rawValue = bareCmp.group(CMP_GROUP_RIGHT).trim();
      Object actual = variableReplacer.lookupValue(varName, variables);
      Object expected = parseLiteral(rawValue);
      return compare(actual, op, expected);
    }
    // 先做变量替换（${var} -> 实际值）
    String resolved = variableReplacer.replacePlaceholders(expr, variables);
    // 解析比较表达式 lhs op rhs
    Matcher m = FlowExpressionUtils.COMPARE_LITERAL.matcher(resolved);
    if (m.matches() && isComparisonOperator(m.group(2))) {
      String rawLhs = m.group(1).trim();
      String op = m.group(2);
      String rawValue = m.group(CMP_GROUP_RIGHT).trim();
      Object actual = parseLiteral(rawLhs);
      Object expected = parseLiteral(rawValue);
      return compare(actual, op, expected);
    }
    // 布尔字面量
    if ("true".equalsIgnoreCase(resolved)) {
      return true;
    }
    if ("false".equalsIgnoreCase(resolved)) {
      return false;
    }
    log.warn("[FlowExpressionEvaluator] 条件表达式无法识别: expr={} resolved={}", expr, resolved);
    return false;
  }

  /**
   * 判断字符串是否为比较运算符
   *
   * @param s 待判断字符串
   * @return 是否为比较运算符
   */
  boolean isComparisonOperator(String s) {
    return ">=".equals(s)
        || "<=".equals(s)
        || "==".equals(s)
        || "!=".equals(s)
        || ">".equals(s)
        || "<".equals(s);
  }

  /**
   * 解析字面量：支持 Boolean、数字（整数/浮点）、字符串（去引号）
   *
   * @param raw 原始字符串
   * @return 解析后的字面量对象
   */
  Object parseLiteral(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.equalsIgnoreCase("true")) {
      return Boolean.TRUE;
    }
    if (s.equalsIgnoreCase("false")) {
      return Boolean.FALSE;
    }
    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length() - 1);
    }
    try {
      if (s.contains(".")) {
        return Double.parseDouble(s);
      }
      return Long.parseLong(s);
    } catch (NumberFormatException nfe) {
      return s;
    }
  }

  /**
   * 比较两个值的大小
   *
   * <p>数值类型按 double 比较；非数值类型按字符串字典序比较。
   *
   * @param actual 实际值
   * @param op 比较运算符（&gt;、&gt;=、&lt;、&lt;=、==、!=）
   * @param expected 期望值
   * @return 比较结果
   */
  boolean compare(Object actual, String op, Object expected) {
    if (actual == null && expected == null) {
      return "==".equals(op) || "!=".equals(op) ? "==".equals(op) : false;
    }
    if (actual == null || expected == null) {
      return false;
    }
    if (actual instanceof Number && expected instanceof Number) {
      double a = ((Number) actual).doubleValue();
      double b = ((Number) expected).doubleValue();
      return switch (op) {
        case ">" -> a > b;
        case ">=" -> a >= b;
        case "<" -> a < b;
        case "<=" -> a <= b;
        case "==" -> Double.compare(a, b) == 0;
        case "!=" -> Double.compare(a, b) != 0;
        default -> false;
      };
    }
    int cmp = String.valueOf(actual).compareTo(String.valueOf(expected));
    return switch (op) {
      case "==" -> cmp == 0;
      case "!=" -> cmp != 0;
      case ">" -> cmp > 0;
      case "<" -> cmp < 0;
      case ">=" -> cmp >= 0;
      case "<=" -> cmp <= 0;
      default -> false;
    };
  }
}
