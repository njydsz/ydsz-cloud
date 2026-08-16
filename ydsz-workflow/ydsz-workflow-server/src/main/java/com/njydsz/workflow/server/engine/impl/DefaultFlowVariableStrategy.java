package com.njydsz.workflow.server.engine.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.expr.ExpressionEvaluator;
import com.njydsz.workflow.server.engine.FlowVariableStrategy;

/**
 * 默认流程变量表达式解析策略
 *
 * <p>本组件是工作流条件评估的统一入口，内部优先委托 Aviator 表达式引擎（ydsz-literule 模块） 进行求值，以统一项目中的表达式引擎，避免多引擎并存导致的语义不一致问题。
 *
 * <h3>P1-3 引擎收敛：Aviator 单引擎策略</h3>
 *
 * <ol>
 *   <li>若 Spring 容器中存在 {@link ExpressionEvaluator} Bean，则优先使用 Aviator 求值（主路径）
 *   <li>若 Aviator 求值失败（表达式语法不兼容等），自动回退到内置正则解析器， 并输出<b>降级告警</b>日志（WARN 级别）
 *   <li>若 Aviator 不可用（literule 模块未启用），直接使用内置正则解析器， 并输出一次性<b>降级告警</b>日志
 * </ol>
 *
 * <p><b>SpEL 已废弃：</b>自 P1-3 起，SpEL 不再作为运行时求值引擎， 条件评估统一收敛为 Aviator，正则解析器仅作兼容性兜底。
 *
 * <h3>向后兼容语法</h3>
 *
 * <ul>
 *   <li>${var} - 简单占位符替换
 *   <li>${var > 100} - 简单比较表达式
 *   <li>${a > 100} && ${b < 50} - 逻辑与（P2-14）
 *   <li>${a > 100} || ${b < 50} - 逻辑或（P2-14）
 *   <li>!${flag} - 逻辑非（P2-14）
 *   <li>${cond ? 'A' : 'B'} - 三元运算符（P2-14）
 *   <li>固定字符串：role:hr / dept:10 / user:1001
 *   <li>纯 Aviator 表达式：amount > 100 && type == 'VIP'（无 ${} 包裹）
 * </ul>
 *
 * <p>当使用 Aviator 引擎时，${} 包裹会被自动剥离，内部表达式直接交给 Aviator 求值。 不带 ${} 的表达式视为纯 Aviator 表达式直接求值。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class DefaultFlowVariableStrategy implements FlowVariableStrategy {

  /**
   * Aviator 表达式求值器（可选注入）。
   *
   * <p>当 ydsz-literule 模块启用时自动注入；未启用时为 null，回退到正则解析。
   */
  private final ExpressionEvaluator expressionEvaluator;

  /** 标记 Aviator 不可用的警告是否已输出过（避免日志刷屏） */
  private volatile boolean aviatorUnavailableLogged = false;

  /**
   * 构造注入：使用 {@link ObjectProvider} 支持可选依赖。
   *
   * @param evaluatorProvider 表达式求值器提供者（可选）
   */
  public DefaultFlowVariableStrategy(ObjectProvider<ExpressionEvaluator> evaluatorProvider) {
    this.expressionEvaluator = evaluatorProvider.getIfAvailable();
  }

  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_\\.]*)}");

  /** 字面量比较：lhs (op) rhs -- lhs 可为标识符、数字、字符串 */
  private static final Pattern COMPARE_LITERAL =
      Pattern.compile("^\\s*(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");

  /** ${var op value} 内部比较模式 -- 即整体被 ${} 包裹且内部含运算符 */
  private static final Pattern COMPARE_INNER =
      Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_\\.]*)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");

  /** 三元表达式：${cond ? trueVal : falseVal} -- 整体被 ${} 包裹 */
  private static final Pattern TERNARY_INNER =
      Pattern.compile("^\\s*(.+?)\\s*\\?\\s*(.+?)\\s*:\\s*(.+?)\\s*$");

  @Override
  public boolean evaluate(String condition, Map<String, Object> variables) {
    if (condition == null || condition.isBlank()) {
      return true;
    }
    // P0-2: dmn: 前缀路由 — DMN 决策表条件应由 FlowRoutingService 处理，
    // 走到这里说明 routingService 不可用（literule 模块未启用或 DecisionTableEvalService 未注入）。
    // 给出明确告警，避免被当作普通表达式静默返回 false。
    if (condition.startsWith("dmn:")) {
      log.warn(
          "[Flow] DMN 决策表路由不可用（FlowRoutingService 未注入），条件评估返回 false: expr='{}'。"
              + "请确保 ydsz-literule 模块已启用且 DecisionTableConfigProvider 已注册。",
          condition);
      return false;
    }
    // 优先使用 Aviator 引擎求值（统一表达式引擎）
    if (expressionEvaluator != null) {
      try {
        // 剥离 ${} 包裹，转换为 Aviator 原生语法
        String aviatorExpr = stripPlaceholders(condition.trim());
        Map<String, Object> facts = variables != null ? variables : Collections.emptyMap();
        RuleContext context = RuleContext.of(facts);
        boolean result = expressionEvaluator.evalBoolean(aviatorExpr, context);
        log.debug(
            "[Flow] Aviator 条件评估: expr='{}' aviatorExpr='{}' -> {}",
            condition,
            aviatorExpr,
            result);
        return result;
      } catch (Exception e) {
        // P1-3: 降级告警 — Aviator 求值失败，回退到自研正则解析器
        log.warn("[Flow][降级告警] Aviator 求值失败，回退到正则解析器: expr='{}' err={}", condition, e.getMessage());
      }
    } else {
      // P1-3: 降级告警 — Aviator 不可用，回退到自研正则解析器（仅输出一次）
      if (!aviatorUnavailableLogged) {
        log.warn(
            "[Flow][降级告警] Aviator 表达式引擎不可用，使用正则解析器降级求值。"
                + "建议启用 ydsz-literule 模块以获得统一的 Aviator 表达式支持。");
        aviatorUnavailableLogged = true;
      }
    }
    // 回退到正则解析器降级路径
    return evaluateLegacy(condition, variables);
  }

  /**
   * 传统正则解析器（回退方案）。
   *
   * <p>当 Aviator 不可用或求值失败时使用，保持原有 ${} 语法兼容。
   *
   * @param condition 条件表达式
   * @param variables 流程变量
   * @return 评估结果
   */
  private boolean evaluateLegacy(String condition, Map<String, Object> variables) {
    String expr = condition.trim();
    try {
      return evaluateOr(expr, variables);
    } catch (Exception e) {
      log.error("[Flow] 条件解析异常: expr={} err={}", condition, e.getMessage());
      return false;
    }
  }

  /** 顶层 || 逻辑或：任一子表达式为 true 即为 true。 例如：${a > 100} || ${b < 50} */
  private boolean evaluateOr(String expr, Map<String, Object> variables) {
    String[] parts = splitTopLevel(expr, "||");
    for (String part : parts) {
      if (evaluateAnd(part.trim(), variables)) {
        return true;
      }
    }
    return false;
  }

  /** 顶层 && 逻辑与：所有子表达式为 true 才为 true。 例如：${a > 100} && ${b < 50} */
  private boolean evaluateAnd(String expr, Map<String, Object> variables) {
    String[] parts = splitTopLevel(expr, "&&");
    for (String part : parts) {
      if (!evaluateNot(part.trim(), variables)) {
        return false;
      }
    }
    return true;
  }

  /** 逻辑非：!expr 形式，支持嵌套（如 !!flag）。 */
  private boolean evaluateNot(String expr, Map<String, Object> variables) {
    String trimmed = expr.trim();
    if (trimmed.startsWith("!")) {
      return !evaluateNot(trimmed.substring(1).trim(), variables);
    }
    return evaluateSingle(trimmed, variables);
  }

  /** 单一原子表达式求值（原有 evaluate 主体逻辑）： - ${var op value} 比较表达式 - ${var} 非空判断 - true/false 字面量 */
  private boolean evaluateSingle(String expr, Map<String, Object> variables) {
    if (expr.isEmpty()) {
      return true;
    }
    // 0. 如果整个表达式是单一 ${...} 占位符（允许非标识符内容如 "${var op value}"）
    Matcher fullPh = Pattern.compile("^\\$\\{(.+)}\\s*$").matcher(expr);
    if (fullPh.matches()) {
      String inner = fullPh.group(1).trim();
      // 先尝试 ${var op value} 格式：内部含运算符
      Matcher innerCmp = COMPARE_INNER.matcher(inner);
      if (innerCmp.matches()) {
        String varName = innerCmp.group(1).trim();
        String op = innerCmp.group(2);
        String rawValue = innerCmp.group(3).trim();
        Object actual = lookupValue(varName, variables);
        Object expected = parseLiteral(rawValue);
        return compare(actual, op, expected);
      }
      // 单一 ${var}：非空 + 非 false 即视为 true
      Object v = lookupValue(inner, variables);
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
    // 通过 lookupValue 获取变量值，避免被当作字符串字面量做字符串比较
    Matcher bareCmp = COMPARE_INNER.matcher(expr);
    if (bareCmp.matches()) {
      String varName = bareCmp.group(1).trim();
      String op = bareCmp.group(2);
      String rawValue = bareCmp.group(3).trim();
      Object actual = lookupValue(varName, variables);
      Object expected = parseLiteral(rawValue);
      return compare(actual, op, expected);
    }
    // 1. 先做变量替换（${var} -> 实际值）
    String resolved = replacePlaceholders(expr, variables);
    // 2. 解析比较表达式 lhs op rhs
    Matcher m = COMPARE_LITERAL.matcher(resolved);
    if (m.matches() && isComparisonOperator(m.group(2))) {
      String rawLhs = m.group(1).trim();
      String op = m.group(2);
      String rawValue = m.group(3).trim();
      Object actual = parseLiteral(rawLhs);
      Object expected = parseLiteral(rawValue);
      return compare(actual, op, expected);
    }
    // 3. 布尔字面量
    if ("true".equalsIgnoreCase(resolved)) {
      return true;
    }
    if ("false".equalsIgnoreCase(resolved)) {
      return false;
    }
    log.warn("[Flow] 条件表达式无法识别: expr={} resolved={}", expr, resolved);
    return false;
  }

  private static boolean isComparisonOperator(String s) {
    return ">=".equals(s)
        || "<=".equals(s)
        || "==".equals(s)
        || "!=".equals(s)
        || ">".equals(s)
        || "<".equals(s);
  }

  @Override
  public String resolveAssignee(String expression, Map<String, Object> variables) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    String trimmed = expression.trim();

    // 优先使用 Aviator 引擎解析（仅对 ${} 包裹的表达式尝试）
    if (expressionEvaluator != null && trimmed.startsWith("${") && trimmed.endsWith("}")) {
      try {
        // 剥离所有 ${} 包裹（含嵌套），转换为 Aviator 原生语法
        String aviatorExpr = stripPlaceholders(trimmed);
        Map<String, Object> facts = variables != null ? variables : Collections.emptyMap();
        RuleContext context = RuleContext.of(facts);
        Object result = expressionEvaluator.eval(aviatorExpr, context);
        if (result != null) {
          String resolved = result.toString();
          log.debug(
              "[Flow] Aviator 办理人解析: expr='{}' aviatorExpr='{}' -> '{}'",
              expression,
              aviatorExpr,
              resolved);
          return resolved;
        }
      } catch (Exception e) {
        log.debug("[Flow] Aviator 办理人解析失败，回退到正则解析器: expr='{}' err={}", expression, e.getMessage());
      }
    }

    // 回退到传统解析逻辑
    return resolveAssigneeLegacy(trimmed, variables);
  }

  /**
   * 传统办理人解析逻辑（回退方案）。
   *
   * <p>当 Aviator 不可用或求值失败时使用，保持原有三元运算符和占位符替换逻辑。
   *
   * @param trimmed 已 trim 的表达式
   * @param variables 流程变量
   * @return 解析结果
   */
  private String resolveAssigneeLegacy(String trimmed, Map<String, Object> variables) {
    // P2-14: 支持三元运算符 ${cond ? trueVal : falseVal}
    // 剥离外层 ${} 后匹配 TERNARY_INNER，避免 cond 残留 ${ 前缀
    String ternaryExpr = trimmed;
    if (ternaryExpr.startsWith("${") && ternaryExpr.endsWith("}")) {
      ternaryExpr = ternaryExpr.substring(2, ternaryExpr.length() - 1).trim();
    }
    Matcher ternary = TERNARY_INNER.matcher(ternaryExpr);
    if (ternary.matches()) {
      String cond = ternary.group(1).trim();
      String trueVal = ternary.group(2).trim();
      String falseVal = ternary.group(3).trim();
      boolean condResult = evaluate(cond, variables);
      String chosen = condResult ? trueVal : falseVal;
      return resolveLiteral(chosen, variables);
    }
    return replacePlaceholders(trimmed, variables);
  }

  /** 解析三元分支的值：支持字符串字面量、${var} 引用、裸标识符。 */
  private String resolveLiteral(String raw, Map<String, Object> variables) {
    String s = raw.trim();
    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length() - 1);
    }
    if (s.startsWith("${") && s.endsWith("}")) {
      String key = s.substring(2, s.length() - 1).trim();
      Object v = lookupValue(key, variables);
      return v == null ? "" : v.toString();
    }
    return s;
  }

  private String replacePlaceholders(String input, Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return input;
    }
    Matcher m = PLACEHOLDER.matcher(input);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(1).trim();
      Object value = lookupValue(key, variables);
      m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value.toString()));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private Object lookupValue(String key, Map<String, Object> variables) {
    if (variables == null) {
      return null;
    }
    // 支持点路径：user.deptId
    if (key.contains(".")) {
      String[] parts = key.split("\\.");
      Object cursor = variables.get(parts[0]);
      for (int i = 1; i < parts.length && cursor != null; i++) {
        if (cursor instanceof Map<?, ?> map) {
          cursor = map.get(parts[i]);
        } else {
          try {
            var field = cursor.getClass().getDeclaredField(parts[i]);
            field.setAccessible(true);
            cursor = field.get(cursor);
          } catch (Exception e) {
            log.warn(
                "[DefaultFlowVariableStrategy] 反射读取字段失败 parts[{}]={}: {}",
                i,
                parts[i],
                e.getMessage());
            return null;
          }
        }
      }
      return cursor;
    }
    return variables.get(key);
  }

  private Object parseLiteral(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
    if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
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

  private boolean compare(Object actual, String op, Object expected) {
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

  /**
   * 将 ${} 包裹的表达式转换为 Aviator 原生语法。
   *
   * <p>遍历表达式字符串，剥离所有 ${ 和匹配的 }，同时保留字符串字面量内部的内容不变。 支持嵌套 ${} 场景（如 ${cond ? ${varA} : ${varB}}）。
   *
   * <p>转换示例：
   *
   * <ul>
   *   <li>${amount > 100} → amount > 100
   *   <li>${a > 100} && ${b < 50} → a > 100 && b < 50
   *   <li>!${flag} → !flag
   *   <li>${type == 'a || b'} → type == 'a || b'
   *   <li>${cond ? ${a} : ${b}} → cond ? a : b
   *   <li>amount > 100（无 ${}）→ amount > 100（原样返回）
   * </ul>
   *
   * @param expr 原始表达式
   * @return 剥离 ${} 后的 Aviator 表达式
   */
  private String stripPlaceholders(String expr) {
    StringBuilder sb = new StringBuilder(expr.length());
    int depth = 0; // ${} 嵌套深度
    boolean inSingle = false;
    boolean inDouble = false;
    int i = 0;
    while (i < expr.length()) {
      char c = expr.charAt(i);
      // 字符串字面量内部不解析 ${}
      if (inSingle) {
        sb.append(c);
        if (c == '\'') inSingle = false;
        i++;
        continue;
      }
      if (inDouble) {
        sb.append(c);
        if (c == '"') inDouble = false;
        i++;
        continue;
      }
      if (c == '\'') {
        inSingle = true;
        sb.append(c);
        i++;
        continue;
      }
      if (c == '"') {
        inDouble = true;
        sb.append(c);
        i++;
        continue;
      }
      // ${ 块开始：跳过 ${ 不输出
      if (c == '$' && i + 1 < expr.length() && expr.charAt(i + 1) == '{') {
        depth++;
        i += 2;
        continue;
      }
      // ${ 块结束：跳过匹配的 } 不输出
      if (c == '}' && depth > 0) {
        depth--;
        i++;
        continue;
      }
      sb.append(c);
      i++;
    }
    return sb.toString().trim();
  }

  /**
   * 在顶层分割字符串，不进入 ${} 块和 '...' / "..." 字面量内部。 例如："${a > 1} && ${b < 2} || ${c == 3}" 按 "||" 分割得到
   * ["${a > 1} && ${b < 2}", " ${c == 3}"]
   *
   * @param expr 待分割的表达式
   * @param delimiter 顶层分隔符（如 "||" 或 "&&"）
   * @return 分割后的子表达式数组
   */
  private String[] splitTopLevel(String expr, String delimiter) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0; // ${} 嵌套深度
    boolean inSingle = false;
    boolean inDouble = false;
    int i = 0;
    while (i < expr.length()) {
      char c = expr.charAt(i);
      // 字符串字面量内部不解析
      if (inSingle) {
        current.append(c);
        if (c == '\'') inSingle = false;
        i++;
        continue;
      }
      if (inDouble) {
        current.append(c);
        if (c == '"') inDouble = false;
        i++;
        continue;
      }
      if (c == '\'') {
        inSingle = true;
        current.append(c);
        i++;
        continue;
      }
      if (c == '"') {
        inDouble = true;
        current.append(c);
        i++;
        continue;
      }
      // ${ 块开始：depth++
      if (c == '$' && i + 1 < expr.length() && expr.charAt(i + 1) == '{') {
        depth++;
        current.append("${");
        i += 2;
        continue;
      }
      // ${ 块结束：depth--
      if (c == '}' && depth > 0) {
        depth--;
        current.append(c);
        i++;
        continue;
      }
      // 顶层匹配分隔符
      if (depth == 0
          && i + delimiter.length() <= expr.length()
          && expr.substring(i, i + delimiter.length()).equals(delimiter)) {
        result.add(current.toString());
        current.setLength(0);
        i += delimiter.length();
        continue;
      }
      current.append(c);
      i++;
    }
    result.add(current.toString());
    return result.toArray(new String[0]);
  }
}
