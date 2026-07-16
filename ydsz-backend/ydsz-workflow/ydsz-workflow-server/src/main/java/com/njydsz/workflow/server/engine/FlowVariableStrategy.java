package com.njydsz.workflow.server.engine;

import java.util.Map;

/**
 * 流程变量 SpEL 表达式解析策略
 *
 * <p>支持 ${var} 占位符 + 简单 SpEL 表达式（如 ${amount > 100000}）。
 *
 * @since 1.0.0
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
