package com.njydsz.agent.server.agent;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import lombok.extern.slf4j.Slf4j;

/**
 * DAG 条件表达式求值器
 *
 * <p>支持以下表达式语法：
 *
 * <ul>
 *   <li><b>变量引用</b>：{@code results['nodeId']} 或 {@code results["nodeId"]}
 *   <li><b>字符串方法</b>：{@code .contains("x")}、{@code .equals("x")}、{@code .startsWith("x")}、{@code
 *       .endsWith("x")}、{@code .isEmpty()}
 *   <li><b>逻辑运算</b>：{@code &&} (AND)、{@code ||} (OR)、{@code !} (NOT)
 *   <li><b>比较运算</b>：{@code ==}、{@code !=}、{@code <}、{@code >}、{@code <=}、{@code >=}（数值比较）
 * </ul>
 *
 * <p>表达式示例：
 *
 * <pre>{@code
 * results['step1'].contains("success")
 * results['step1'].equals("done") && results['step2'].startsWith("ok")
 * !results['step3'].isEmpty()
 * results['counter'] > 5
 * }</pre>
 *
 * <p><b>线程安全</b>：无状态工具类，可安全并发调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class DagConditionEvaluator {

  /** 私有构造器防止实例化 */
  private DagConditionEvaluator() {}

  /** 变量引用模式：results['nodeId'] 或 results["nodeId"] */
  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("results\\['([^']+)'\\]|results\\[\"([^\"]+)\"\\]");

  /** 字符串方法调用模式 */
  private static final Pattern CONTAIN_PATTERN =
      Pattern.compile("\\.contains\\([\"']([^\"']+)[\"']\\)");

  private static final Pattern EQUALS_PATTERN =
      Pattern.compile("\\.equals\\([\"']([^\"']+)[\"']\\)");
  private static final Pattern STARTS_WITH_PATTERN =
      Pattern.compile("\\.startsWith\\([\"']([^\"']+)[\"']\\)");
  private static final Pattern ENDS_WITH_PATTERN =
      Pattern.compile("\\.endsWith\\([\"']([^\"']+)[\"']\\)");

  /** 数值比较运算符（按优先级排列：双字符运算符必须先于单字符运算符匹配） */
  private static final String[] NUMERIC_COMPARISON_OPS = {"<=", ">=", "<", ">"};

  /**
   * 对给定的条件表达式进行求值。
   *
   * @param condition 条件表达式字符串
   * @param results 节点执行结果映射（节点 ID → 输出内容）
   * @return 条件表达式计算结果
   */
  public static boolean evaluate(String condition, Map<String, String> results) {
    if (condition == null || condition.isBlank()) {
      return true;
    }
    try {
      String expr = condition.trim();
      return evaluateExpression(expr, results);
    } catch (Exception e) {
      log.warn("[DAG] 条件求值异常: condition={}, error={}", condition, e.getMessage());
      return false;
    }
  }

  /**
   * 递归求值表达式（支持逻辑运算符）。
   *
   * <p>处理优先级：NOT > AND > OR。
   *
   * @param expr 表达式字符串
   * @param results 结果变量映射
   * @return 求值结果
   */
  private static boolean evaluateExpression(String expr, Map<String, String> results) {
    expr = expr.trim();

    // 处理 NOT
    if (expr.startsWith("!")) {
      return !evaluateExpression(expr.substring(1).trim(), results);
    }

    // 处理括号分组
    if (expr.startsWith("(") && findMatchingParen(expr, 0) == expr.length() - 1) {
      return evaluateExpression(expr.substring(1, expr.length() - 1).trim(), results);
    }

    // 按 || 分割（最低优先级）
    int orIndex = findOperatorAtTopLevel(expr, "||");
    if (orIndex >= 0) {
      String left = expr.substring(0, orIndex).trim();
      String right = expr.substring(orIndex + 2).trim();
      return evaluateExpression(left, results) || evaluateExpression(right, results);
    }

    // 按 && 分割
    int andIndex = findOperatorAtTopLevel(expr, "&&");
    if (andIndex >= 0) {
      String left = expr.substring(0, andIndex).trim();
      String right = expr.substring(andIndex + 2).trim();
      return evaluateExpression(left, results) && evaluateExpression(right, results);
    }

    // 无逻辑运算符，求值原子条件
    return evaluateAtomicCondition(expr, results);
  }

  /**
   * 求值原子条件（不含逻辑运算符的单个条件）。
   *
   * @param expr 原子条件表达式
   * @param results 结果变量映射
   * @return 求值结果
   */
  private static boolean evaluateAtomicCondition(String expr, Map<String, String> results) {
    // 比较运算：==
    int eqIdx = findOperatorAtTopLevel(expr, "==");
    if (eqIdx >= 0) {
      String left = expr.substring(0, eqIdx).trim();
      String right = expr.substring(eqIdx + 2).trim();
      return resolveValue(left, results).equals(stripQuotes(right));
    }

    // 比较运算：!=
    int neIdx = findOperatorAtTopLevel(expr, "!=");
    if (neIdx >= 0) {
      String left = expr.substring(0, neIdx).trim();
      String right = expr.substring(neIdx + 2).trim();
      return !resolveValue(left, results).equals(stripQuotes(right));
    }

    // 数值比较运算：<= / >= / < / >（P1 修复：类注释声明支持但原实现缺失）
    // 注意查找顺序：<= 必须先于 <，>= 必须先于 >，否则双字符运算符会被单字符截断
    for (String op : NUMERIC_COMPARISON_OPS) {
      int opIdx = findOperatorAtTopLevel(expr, op);
      if (opIdx >= 0) {
        double left = parseNumeric(resolveValue(expr.substring(0, opIdx).trim(), results));
        double right = parseNumeric(stripQuotes(expr.substring(opIdx + op.length()).trim()));
        return switch (op) {
          case "<=" -> left <= right;
          case ">=" -> left >= right;
          case "<" -> left < right;
          default -> left > right;
        };
      }
    }

    // 字符串方法调用
    if (expr.contains(".contains(")) {
      return evaluateStringMethod(expr, CONTAIN_PATTERN, results, String::contains);
    }
    if (expr.contains(".equals(")) {
      return evaluateStringMethod(expr, EQUALS_PATTERN, results, String::equals);
    }
    if (expr.contains(".startsWith(")) {
      return evaluateStringMethod(expr, STARTS_WITH_PATTERN, results, String::startsWith);
    }
    if (expr.contains(".endsWith(")) {
      return evaluateStringMethod(expr, ENDS_WITH_PATTERN, results, String::endsWith);
    }
    if (expr.contains(".isEmpty()")) {
      String varPart = expr.substring(0, expr.indexOf(".isEmpty()")).trim();
      return resolveValue(varPart, results).isEmpty();
    }
    if (expr.contains(".isNotEmpty()")) {
      String varPart = expr.substring(0, expr.indexOf(".isNotEmpty()")).trim();
      return !resolveValue(varPart, results).isEmpty();
    }

    // 纯变量引用（非空为 true）
    String resolved = resolveValue(expr, results);
    return resolved != null
        && !resolved.isBlank()
        && !"false".equalsIgnoreCase(resolved)
        && !"null".equalsIgnoreCase(resolved);
  }

  /**
   * 求值字符串方法调用。
   *
   * @param expr 完整表达式
   * @param methodPattern 方法匹配正则
   * @param results 结果变量映射
   * @param operation 字符串操作函数
   * @return 方法调用结果
   */
  private static boolean evaluateStringMethod(
      String expr,
      Pattern methodPattern,
      Map<String, String> results,
      BiFunction<String, String, Boolean> operation) {
    int methodStart = expr.indexOf(".");
    String varPart = expr.substring(0, methodStart).trim();
    String resolved = resolveValue(varPart, results);

    Matcher matcher = methodPattern.matcher(expr);
    if (matcher.find()) {
      String arg = matcher.group(1);
      return operation.apply(resolved, arg);
    }
    return false;
  }

  /**
   * 解析变量值。
   *
   * <p>支持：
   *
   * <ul>
   *   <li>results['nodeId'] → 从结果映射获取
   *   <li>"literal" 或 'literal' → 字符串字面量
   *   <li>其他 → 直接作为字面量返回
   * </ul>
   *
   * @param expr 变量表达式
   * @param results 结果变量映射
   * @return 解析后的值
   */
  private static String resolveValue(String expr, Map<String, String> results) {
    if (expr == null) {
      return "";
    }
    expr = expr.trim();

    // results['nodeId'] 或 results["nodeId"]
    Matcher varMatcher = VARIABLE_PATTERN.matcher(expr);
    if (varMatcher.matches()) {
      String nodeId = varMatcher.group(1) != null ? varMatcher.group(1) : varMatcher.group(2);
      return results.getOrDefault(nodeId, "");
    }

    // 字符串字面量
    return stripQuotes(expr);
  }

  /**
   * 解析字符串为数值（供数值比较运算符使用）。
   *
   * @param value 数值字符串
   * @return 解析后的 double 值
   * @throws IllegalArgumentException 当字符串无法解析为数字时抛出
   */
  private static double parseNumeric(String value) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("数值比较的操作数无法解析为数字: " + value, e);
    }
  }

  /**
   * 去除字符串两端的引号。
   *
   * @param value 带引号的字符串
   * @return 去除引号后的字符串
   */
  private static String stripQuotes(String value) {
    if (value == null) {
      return "";
    }
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))) {
      if (value.length() >= 2) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  /**
   * 在顶层（不在括号内）查找运算符位置。
   *
   * @param expr 表达式字符串
   * @param operator 运算符字符串
   * @return 运算符位置；未找到返回 -1
   */
  private static int findOperatorAtTopLevel(String expr, String operator) {
    int depth = 0;
    for (int i = 0; i <= expr.length() - operator.length(); i++) {
      char c = expr.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (depth == 0 && expr.startsWith(operator, i)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * 查找与起始括号匹配的闭合括号位置。
   *
   * @param expr 表达式字符串
   * @param start 起始括号位置
   * @return 匹配的闭合括号位置；未找到返回 -1
   */
  private static int findMatchingParen(String expr, int start) {
    int depth = 0;
    for (int i = start; i < expr.length(); i++) {
      if (expr.charAt(i) == '(') {
        depth++;
      } else if (expr.charAt(i) == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }
}
