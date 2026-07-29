package com.njydsz.common.domain.dag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import lombok.extern.slf4j.Slf4j;

/**
 * SpEL 条件表达式评估器（DAG 条件分支节点使用）。
 *
 * <p>用于 DAG 条件分支节点（CONDITION）的表达式评估，
 * 支持从上下文中读取上游节点的执行结果进行条件判断。
 *
 * <p>表达式格式：{@code ${nodeA.result=='success'}} 或 {@code #nodeA.status!='FAILED'}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SpELConditionEvaluator {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /** 表达式解析缓存，避免重复解析相同表达式 */
    private static final ConcurrentHashMap<String, Expression> EXPR_CACHE = new ConcurrentHashMap<>();

    /**
     * 评估条件表达式。
     *
     * @param expression 条件表达式（支持 {@code ${...}} 包裹或纯 SpEL 表达式）
     * @param context    上下文变量（key=变量名, value=变量值）
     * @return 评估结果；表达式为空或解析失败时返回 false
     */
    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return false;
        }

        String spel = expression.trim();
        if (spel.startsWith("${") && spel.endsWith("}")) {
            spel = spel.substring(2, spel.length() - 1).trim();
        }

        try {
            EvaluationContext evalContext = buildEvaluationContext(context);
            Expression parsed = EXPR_CACHE.computeIfAbsent(spel, PARSER::parseExpression);
            Boolean result = parsed.getValue(evalContext, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("[SpELConditionEvaluator] 表达式评估失败, 返回 false: expr={} reason={}",
                    expression, e.getMessage());
            return false;
        }
    }

    private EvaluationContext buildEvaluationContext(Map<String, Object> context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.addPropertyAccessor(new MapAccessor());
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                evalContext.setVariable(entry.getKey(), entry.getValue());
            }
        }
        return evalContext;
    }
}
