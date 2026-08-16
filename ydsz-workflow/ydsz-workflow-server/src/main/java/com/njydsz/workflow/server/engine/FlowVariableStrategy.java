package com.njydsz.workflow.server.engine;

import java.util.Map;

/**
 * 流程变量表达式解析策略
 *
 * <p>P1-3 引擎收敛：主路径使用 Aviator 表达式引擎（通过 ydsz-literule）， 自研正则解析器仅作为 Aviator 不可用时的降级路径。不支持 SpEL。
 *
 * <p>支持 ${var} 占位符 + Aviator 表达式（如 ${amount > 100000}） 以及向后兼容的正则语法（仅降级时启用）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface FlowVariableStrategy {

  /**
   * 解析条件表达式
   *
   * @return true 条件成立，false 不成立
   */
  boolean evaluate(String condition, Map<String, Object> variables);

  /**
   * 解析办理人表达式
   *
   * @param expression 形如 role:hr / dept:10 / user:1001 / ${expression}
   * @return 解析结果（按实现不同返回不同语义）
   */
  String resolveAssignee(String expression, Map<String, Object> variables);
}
