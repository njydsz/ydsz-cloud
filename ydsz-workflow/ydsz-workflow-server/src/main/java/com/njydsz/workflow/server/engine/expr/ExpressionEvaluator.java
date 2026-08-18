package com.njydsz.workflow.server.engine.expr;

import java.util.Map;

/**
 * 工作流内置表达式求值器接口
 *
 * <p>引擎自包含的表达式求值 SPI，默认提供基于 Aviator 的实现（{@link AviatorExpressionEvaluator}）。
 *
 * <p>业务系统可通过实现本接口并注册为 Bean 来替换默认求值逻辑（如接入 ydsz-literule 引擎）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface ExpressionEvaluator {

  /**
   * 求值布尔表达式
   *
   * @param expression 表达式字符串
   * @param variables 变量上下文
   * @return 表达式结果；求值异常返回 false
   */
  boolean evalBoolean(String expression, Map<String, Object> variables);

  /**
   * 求值表达式（通用类型）
   *
   * @param expression 表达式字符串
   * @param variables 变量上下文
   * @return 表达式结果；求值异常返回 null
   */
  Object eval(String expression, Map<String, Object> variables);
}
