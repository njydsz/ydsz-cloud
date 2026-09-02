package com.njydsz.workflow.server.engine.impl;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 办理人表达式解析器（正则路径）
 *
 * <p>实现基于正则的办理人表达式解析，支持以下语法：
 *
 * <ul>
 *   <li>固定字符串：role:hr / dept:10 / user:1001
 *   <li>${var} - 占位符引用
 *   <li>${cond ? 'A' : 'B'} - 三元运算符（P2-14）
 * </ul>
 *
 * <p>本组件作为 Aviator 引擎不可用时的降级路径（legacy path），保留原有三元运算符和占位符替换逻辑。
 *
 * <p>注意：本类的 {@link #resolveAssigneeLegacy} 需接收外部求值函数以处理三元条件中的子表达式求值。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowAssigneeExpressionResolver {

    /** 三元表达式正则捕获组：false 分支值 */
  private static final int TERNARY_GROUP_FALSE = 3;

  private final FlowVariableReplacer variableReplacer;

  /**
   * 构造注入变量替换器
   *
   *
   * @param variableReplacer 变量占位符替换器
   */
  public FlowAssigneeExpressionResolver(FlowVariableReplacer variableReplacer) {
    this.variableReplacer = variableReplacer;
  }

  /**
   * 传统办理人解析逻辑（回退方案）
   *
   * <p>当 Aviator 不可用或求值失败时使用，保持原有三元运算符和占位符替换逻辑。
   *
   * @param trimmed 已 trim 的表达式
   * @param variables 流程变量
   * @param evaluator 条件求值委托（用于三元表达式条件判断），由调用方传入以打破循环依赖
   * @return 解析结果
   */
  public String resolveAssigneeLegacy(
      String trimmed,
      Map<String, Object> variables,
      BiFunction<String, Map<String, Object>, Boolean> evaluator) {
    // P2-14: 支持三元运算符 ${cond ? trueVal : falseVal}
    // 剥离外层 ${} 后匹配 TERNARY_INNER，避免 cond 残留 ${ 前缀
    String ternaryExpr = trimmed;
    if (ternaryExpr.startsWith("${") && ternaryExpr.endsWith("}")) {
      ternaryExpr = ternaryExpr.substring(2, ternaryExpr.length() - 1).trim();
    }
    Matcher ternary = FlowExpressionUtils.TERNARY_INNER.matcher(ternaryExpr);
    if (ternary.matches()) {
      String cond = ternary.group(1).trim();
      String trueVal = ternary.group(2).trim();
      String falseVal = ternary.group(TERNARY_GROUP_FALSE).trim();
      boolean condResult = evaluator.apply(cond, variables);
      String chosen = condResult ? trueVal : falseVal;
      return resolveLiteral(chosen, variables);
    }
    return variableReplacer.replacePlaceholders(trimmed, variables);
  }

  /**
   * 解析三元分支的值
   *
   * <p>支持字符串字面量（带引号）、${var} 引用、裸标识符三种形式。
   *
   * @param raw 原始值字符串
   * @param variables 流程变量
   * @return 解析后的字符串值
   */
  String resolveLiteral(String raw, Map<String, Object> variables) {
    String s = raw.trim();
    if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length() - 1);
    }
    if (s.startsWith("${") && s.endsWith("}")) {
      String key = s.substring(2, s.length() - 1).trim();
      Object v = variableReplacer.lookupValue(key, variables);
      return v == null ? "" : v.toString();
    }
    return s;
  }
}
