package com.njydsz.literule.server.impl;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.expression.ExpressionEngine;

/**
 * 编译后的决策表条件（P1-5：决策表预编译，消除运行时正则）
 *
 * <p>在注册期将条件字符串解析为 {@link CompiledCondition} 对象，缓存复用，
 * 避免每次评估时的正则解析开销。
 *
 * <p>支持的条件类型：
 *
 * <ul>
 *   <li>{@code expr:...} — LiteExpr 表达式（运行时求值，不预编译）
 *   <li>{@code null} / {@code ==null} / {@code !=null} — 空值匹配
 *   <li>{@code [0.05,0.15)} — 区间匹配（预编译为 {@link IntervalCondition}）
 *   <li>{@code RED|YELLOW} — 枚举匹配（预编译为 {@link EnumCondition}）
 *   <li>{@code >=3} / {@code <0.05} — 比较表达式（预编译为 {@link ComparisonCondition}）
 *   <li>字面值 — 相等匹配（预编译为 {@link LiteralCondition}）
 *   <li>{@code *} 或空白 — 恒真（预编译为 {@link AlwaysTrueCondition}）
 * </ul>
 *
 * @since 1.4.0
 * @author ydsz-team
 */
public interface CompiledCondition {

    /** CompiledCondition 日志记录器 */
    Logger LOG = LoggerFactory.getLogger(CompiledCondition.class);

    /** LiteExpr 表达式前缀 */
    String EXPR_PREFIX = "expr:";

    /** 比较表达式正则 */
    Pattern COMPARISON_PATTERN = Pattern.compile("^(>=|<=|>|<|!=|==)\\s*(.+)$");

    /** 区间表达式正则 */
    Pattern INTERVAL_PATTERN = Pattern.compile("^(\\[|\\()([^,]+),([^\\]\\)]+)(\\]|\\))$");

    /** 枚举表达式正则 */
    Pattern ENUM_PATTERN = Pattern.compile("^([^|]+(?:\\|[^|]+)+)$");

    /**
     * 匹配事实值
     *
     * @param factValue 事实值
     * @param context 规则上下文（expr: 表达式求值使用）
     * @param evaluator 表达式求值器（expr: 表达式求值使用）
     * @return true 表示匹配
     */
    boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator);

    /**
     * 编译条件字符串为 {@link CompiledCondition}
     *
     * <p>编译失败时返回 {@link FallbackCondition}（降级为运行时解析）。
     *
     * @param condExpr 条件表达式
     * @return 编译后的条件
     */
    static CompiledCondition compile(String condExpr) {
        if (condExpr == null) {
            return new AlwaysTrueCondition();
        }
        String trimmed = condExpr.trim();
        if (trimmed.isEmpty() || "*".equals(trimmed)) {
            return new AlwaysTrueCondition();
        }

        // LiteExpr 表达式：不预编译，运行时求值
        if (trimmed.startsWith(EXPR_PREFIX)) {
            return new ExprCondition(trimmed.substring(EXPR_PREFIX.length()));
        }

        // null 检查
        if ("null".equalsIgnoreCase(trimmed) || "==null".equals(trimmed)) {
            return new NullCondition(true);
        }
        if ("!=null".equals(trimmed)) {
            return new NullCondition(false);
        }

        // 区间：[0.05,0.15)
        Matcher intervalMatcher = INTERVAL_PATTERN.matcher(trimmed);
        if (intervalMatcher.matches()) {
            try {
                return new IntervalCondition(intervalMatcher);
            } catch (Exception e) {
                LOG.warn("[CompiledCondition] 区间条件编译失败，降级为运行时解析: {}", condExpr);
                return new FallbackCondition(condExpr);
            }
        }

        // 枚举：RED|YELLOW
        Matcher enumMatcher = ENUM_PATTERN.matcher(trimmed);
        if (enumMatcher.matches() && trimmed.contains("|")) {
            return new EnumCondition(trimmed);
        }

        // 比较：>=3, <0.05
        Matcher cmpMatcher = COMPARISON_PATTERN.matcher(trimmed);
        if (cmpMatcher.matches()) {
            try {
                return new ComparisonCondition(cmpMatcher);
            } catch (Exception e) {
                LOG.warn("[CompiledCondition] 比较条件编译失败，降级为运行时解析: {}", condExpr);
                return new FallbackCondition(condExpr);
            }
        }

        // 字面值：相等匹配
        return new LiteralCondition(trimmed);
    }

  // ==================== 实现类 ====================

  /**
   * 恒真条件
   */
  class AlwaysTrueCondition implements CompiledCondition {
    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      return true;
    }
  }

  /**
   * 空值匹配条件
   *
   * @param matchNull true 表示匹配 null；false 表示匹配非 null
   */
  class NullCondition implements CompiledCondition {
    private final boolean matchNull;

    NullCondition(boolean matchNull) {
      this.matchNull = matchNull;
    }

    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      return matchNull ? factValue == null : factValue != null;
    }
  }

  /**
   * 区间匹配条件
   */
  class IntervalCondition implements CompiledCondition {
    private final boolean leftInclusive;
    private final boolean rightInclusive;
    private final double min;
    private final double max;

    IntervalCondition(Matcher matcher) {
      this.leftInclusive = "[".equals(matcher.group(1));
      this.rightInclusive = "]".equals(matcher.group(4));
      this.min = parseDouble(matcher.group(2));
      this.max = parseDouble(matcher.group(3));
    }

    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      if (factValue == null) {
        return false;
      }
      double value = toDouble(factValue);
      boolean leftMatch = leftInclusive ? value >= min : value > min;
      boolean rightMatch = rightInclusive ? value <= max : value < max;
      return leftMatch && rightMatch;
    }
  }

  /**
   * 枚举匹配条件
   */
  class EnumCondition implements CompiledCondition {
    private final Set<String> values;

    EnumCondition(String expr) {
      this.values = new HashSet<>();
      for (String part : expr.split("\\|")) {
        values.add(part.trim());
      }
    }

    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      return factValue != null && values.contains(factValue.toString());
    }
  }

  /**
   * 比较匹配条件
   */
  class ComparisonCondition implements CompiledCondition {
    private final String operator;
    private final double threshold;

    ComparisonCondition(Matcher matcher) {
      this.operator = matcher.group(1);
      this.threshold = parseDouble(matcher.group(3));
    }

    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      if (factValue == null) {
        return false;
      }
      double value = toDouble(factValue);
      return switch (operator) {
        case ">" -> value > threshold;
        case ">=" -> value >= threshold;
        case "<" -> value < threshold;
        case "<=" -> value <= threshold;
        case "==" -> value == threshold;
        case "!=" -> value != threshold;
        default -> false;
      };
    }
  }

  /**
   * 字面值相等匹配条件
   */
  class LiteralCondition implements CompiledCondition {
    private final String value;

    LiteralCondition(String value) {
      this.value = value;
    }

    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      return Objects.equals(value, factValue != null ? factValue.toString() : null);
    }
  }

  /**
   * LiteExpr 表达式条件（运行时求值）
   */
  class ExprCondition implements CompiledCondition {
    private final String expression;

    ExprCondition(String expression) {
      this.expression = expression;
    }

    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      try {
        return evaluator != null && evaluator.evalBoolean(expression, context);
      } catch (Exception e) {
        LOG.debug("[CompiledCondition] 表达式条件求值失败 expr={}: {}", expression, e.getMessage());
        return false;
      }
    }
  }

  /**
   * 降级条件（编译失败时使用运行时解析）
   */
  class FallbackCondition implements CompiledCondition {
    private final String condExpr;

    FallbackCondition(String condExpr) {
      this.condExpr = condExpr;
    }

    @Override
    public boolean matches(Object factValue, RuleContext context, ExpressionEngine evaluator) {
      // 降级为原始的 ConditionMatcher 运行时解析
      return ConditionMatcher.match("fallback", condExpr, factValue, context, evaluator);
    }
  }

  // ==================== 工具方法 ====================

  /**
   * 解析 double 值（支持整数和小数）
   */
  static double parseDouble(String str) {
    return new BigDecimal(str.trim()).doubleValue();
  }

  /**
   * 将对象转换为 double
   */
  static double toDouble(Object obj) {
    if (obj instanceof Number num) {
      return num.doubleValue();
    }
    return Double.parseDouble(obj.toString());
  }
}
