package com.njydsz.pmis.common.core.dag;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SpEL 条件表达式求值器（DAG 边条件判断）。
 *
 * <p>用于 DAG 边的条件表达式求值，决定是否触发后继节点。
 * 表达式示例：{@code #result == true}、{@code #output.status == 'SUCCESS'}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class SpELConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 求值条件表达式。
     *
     * @param expression SpEL 表达式
     * @param context    上下文变量
     * @return 表达式求值结果，表达式为 null 或空时返回 true
     */
    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        try {
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            if (context != null) {
                context.forEach(evalContext::setVariable);
            }
            Expression exp = parser.parseExpression(expression);
            Boolean result = exp.getValue(evalContext, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
