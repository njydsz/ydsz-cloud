package com.njydsz.workflow.server.engine.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

/**
 * 表达式解析工具类
 *
 * <p>提供 ${} 占位符处理、顶层表达式分割等通用工具方法以及正则常量定义，供条件求值器和办理人解析器共同使用。
 *
 * <p>本工具类为纯静态工具，不纳入 Spring 容器管理。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public final class FlowExpressionUtils {

  /** 匹配 ${varName} 占位符，捕获组 1 为变量名（含点路径） */
  public static final Pattern PLACEHOLDER =
      Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_\\.]*)}");

  /** 字面量比较：lhs (op) rhs -- lhs 可为标识符、数字、字符串 */
  public static final Pattern COMPARE_LITERAL =
      Pattern.compile("^\\s*(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");

  /** ${var op value} 内部比较模式 -- 即整体被 ${} 包裹且内部含运算符 */
  public static final Pattern COMPARE_INNER =
      Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_\\.]*)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");

  /** 三元表达式：${cond ? trueVal : falseVal} -- 整体被 ${} 包裹 */
  public static final Pattern TERNARY_INNER =
      Pattern.compile("^\\s*(.+?)\\s*\\?\\s*(.+?)\\s*:\\s*(.+?)\\s*$");

  /** 私有构造禁止实例化 */
  private FlowExpressionUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 将 ${} 包裹的表达式转换为 Aviator 原生语法
   *
   * <p>遍历表达式字符串，剥离所有 ${ 和匹配的 }，同时保留字符串字面量内部的内容不变。
   * 支持嵌套 ${} 场景（如 ${cond ? ${varA} : ${varB}}）。
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
  public static String stripPlaceholders(String expr) {
    StringBuilder sb = new StringBuilder(expr.length());
    int depth = 0;
    boolean inSingle = false;
    boolean inDouble = false;
    int i = 0;
    while (i < expr.length()) {
      char c = expr.charAt(i);
      if (inSingle) {
        sb.append(c);
        if (c == '\'') {
          inSingle = false;
        }
        i++;
        continue;
      }
      if (inDouble) {
        sb.append(c);
        if (c == '"') {
          inDouble = false;
        }
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
      if (c == '$' && i + 1 < expr.length() && expr.charAt(i + 1) == '{') {
        depth++;
        i += 2;
        continue;
      }
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
   * 在顶层分割字符串，不进入 ${} 块和 '...' / "..." 字面量内部
   *
   * <p>例如："${a > 1} && ${b < 2} || ${c == 3}" 按 "||" 分割得到 ["${a > 1} && ${b < 2}", "
   * ${c == 3}"]
   *
   * @param expr 待分割的表达式
   * @param delimiter 顶层分隔符（如 "||" 或 "&&"）
   * @return 分割后的子表达式数组
   */
  public static String[] splitTopLevel(String expr, String delimiter) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    boolean inSingle = false;
    boolean inDouble = false;
    int i = 0;
    while (i < expr.length()) {
      char c = expr.charAt(i);
      if (inSingle) {
        current.append(c);
        if (c == '\'') {
          inSingle = false;
        }
        i++;
        continue;
      }
      if (inDouble) {
        current.append(c);
        if (c == '"') {
          inDouble = false;
        }
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
      if (c == '$' && i + 1 < expr.length() && expr.charAt(i + 1) == '{') {
        depth++;
        current.append("${");
        i += 2;
        continue;
      }
      if (c == '}' && depth > 0) {
        depth--;
        current.append(c);
        i++;
        continue;
      }
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
