package com.njydsz.literule.server.impl;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;

/**
 * 条件表达式匹配器（P0-3 抽取自 DecisionTableRule，供决策表/交叉决策表复用）
 *
 * <p>支持与决策表条件一致的匹配语义：
 *
 * <ul>
 *   <li>{@code expr:...} — LiteExpr 表达式
 *   <li>{@code null} / {@code ==null} / {@code !=null} — 空值匹配
 *   <li>{@code [0.05,0.15)} — 区间匹配（含边界/开闭括号）
 *   <li>{@code RED|YELLOW} — 枚举匹配
 *   <li>{@code >=3} / {@code <0.05} — 比较表达式
 *   <li>字面值 — 相等匹配（字符串/数值）
 *   <li>{@code *} 或空白 — 恒真（通配）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
final class ConditionMatcher {

  /** 比较表达式正则捕获组：右侧操作数 */
  private static final int CMP_GROUP_RIGHT = 3;

  /** 比较表达式正则捕获组：右括号 */
  private static final int CMP_GROUP_BRACKET = 4;

  private static final Pattern COMPARISON_PATTERN = Pattern.compile("^(>=|<=|>|<|!=|==)\\s*(.+)$");
  private static final Pattern INTERVAL_PATTERN =
      Pattern.compile("^(\\[|\\()([^,]+),([^\\]\\)]+)(\\]|\\))$");
  private static final Pattern ENUM_PATTERN = Pattern.compile("^([^|]+(?:\\|[^|]+)+)$");
  private static final String EXPR_PREFIX = "expr:";

  private ConditionMatcher() {}

  /**
   * 匹配单个条件表达式
   *
   * @param column 条件列/字段名（用于日志定位）
   * @param condExpr 条件表达式（可为 null/空白/*）
   * @param factValue 事实值
   * @param context 规则上下文（expr: 表达式求值使用）
   * @param evaluator 表达式求值器（expr: 表达式求值使用）
   * @return true=命中
   */
  static boolean match(
      String column,
      String condExpr,
      Object factValue,
      RuleContextVO context,
      ExpressionEngine evaluator) {
    if (condExpr == null) {
      return true;
    }
    condExpr = condExpr.trim();
    if (condExpr.isEmpty() || "*".equals(condExpr)) {
      return true;
    }

    // LiteExpr 表达式：expr:>amount*0.1
    if (condExpr.startsWith(EXPR_PREFIX)) {
      String expr = condExpr.substring(EXPR_PREFIX.length());
      try {
        return evaluator != null && evaluator.evalBoolean(expr, context);
      } catch (Exception e) {
        log.debug(
            "[ConditionMatcher] 表达式条件求值失败 column={} expr={}: {}",
            column,
            expr,
            e.getMessage());
        return false;
      }
    }

    // null 检查：支持 "null"、"==null" 匹配 null；"!=null" 匹配非 null（此处 factValue 为 null 所以返回 false）
    if (factValue == null) {
      return "null".equalsIgnoreCase(condExpr) || "==null".equals(condExpr);
    }

    // 区间：[0.05,0.15)
    Matcher intervalMatcher = INTERVAL_PATTERN.matcher(condExpr);
    if (intervalMatcher.matches()) {
      return matchInterval(intervalMatcher, factValue);
    }

    // 枚举：RED|YELLOW
    Matcher enumMatcher = ENUM_PATTERN.matcher(condExpr);
    if (enumMatcher.matches() && condExpr.contains("|")) {
      String[] parts = condExpr.split("\\|");
      for (String part : parts) {
        if (Objects.equals(toString(factValue), part.trim())) {
          return true;
        }
      }
      return false;
    }

    // 比较表达式：>=3 / <0.05 / !=null
    Matcher comparisonMatcher = COMPARISON_PATTERN.matcher(condExpr);
    if (comparisonMatcher.matches()) {
      String op = comparisonMatcher.group(1);
      String operandStr = comparisonMatcher.group(2).trim();
      if ("null".equalsIgnoreCase(operandStr)) {
        return (op.equals("==") && factValue == null) || (op.equals("!=") && factValue != null);
      }
      return matchComparison(op, operandStr, factValue);
    }

    // 字面值相等
    return Objects.equals(toString(factValue), condExpr) || equalsNumeric(factValue, condExpr);
  }

  private static boolean matchInterval(Matcher m, Object factValue) {
    try {
      BigDecimal fact = toBigDecimal(factValue);
      if (fact == null) {
        return false;
      }
      String leftBracket = m.group(1);
      BigDecimal left = new BigDecimal(m.group(2).trim());
      String rightStr = m.group(CMP_GROUP_RIGHT).trim();
      String rightBracket = m.group(CMP_GROUP_BRACKET);
      BigDecimal right = new BigDecimal(rightStr);

      boolean leftOk = leftBracket.equals("[") ? fact.compareTo(left) >= 0 : fact.compareTo(left) > 0;
      boolean rightOk =
          rightBracket.equals("]") ? fact.compareTo(right) <= 0 : fact.compareTo(right) < 0;
      return leftOk && rightOk;
    } catch (Exception e) {
      log.warn("[ConditionMatcher] 区间匹配异常 factValue={}: {}", factValue, e.getMessage());
      return false;
    }
  }

  private static boolean matchComparison(String op, String operandStr, Object factValue) {
    try {
      // 字符串比较
      if ("==".equals(op)) {
        return Objects.equals(toString(factValue), operandStr)
            || equalsNumeric(factValue, operandStr);
      }
      if ("!=".equals(op)) {
        return !Objects.equals(toString(factValue), operandStr)
            && !equalsNumeric(factValue, operandStr);
      }
      // 数值比较
      BigDecimal fact = toBigDecimal(factValue);
      BigDecimal operand = new BigDecimal(operandStr);
      if (fact == null) {
        return false;
      }
      int cmp = fact.compareTo(operand);
      return switch (op) {
        case ">" -> cmp > 0;
        case ">=" -> cmp >= 0;
        case "<" -> cmp < 0;
        case "<=" -> cmp <= 0;
        default -> false;
      };
    } catch (Exception e) {
      log.warn(
          "[ConditionMatcher] 比较匹配异常 op={} operandStr={} factValue={}: {}",
          op,
          operandStr,
          factValue,
          e.getMessage());
      return false;
    }
  }

  private static boolean equalsNumeric(Object factValue, String operandStr) {
    try {
      BigDecimal fact = toBigDecimal(factValue);
      if (fact == null) {
        return false;
      }
      BigDecimal operand = new BigDecimal(operandStr.trim());
      return fact.compareTo(operand) == 0;
    } catch (Exception e) {
      log.warn(
          "[ConditionMatcher] 数值相等比较异常 factValue={} operandStr={}: {}",
          factValue,
          operandStr,
          e.getMessage());
      return false;
    }
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return new BigDecimal(n.toString());
    }
    try {
      return new BigDecimal(value.toString().trim());
    } catch (Exception e) {
      log.warn("[ConditionMatcher] BigDecimal 转换失败 value={}: {}", value, e.getMessage());
      return null;
    }
  }

  private static String toString(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd.toPlainString();
    }
    return String.valueOf(value);
  }
}
