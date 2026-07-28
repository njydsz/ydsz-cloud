package com.njydsz.common.core.dag;

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
 * SpEL 条件表达式评估器（P1-8 DAG 条件分支）。
 *
 * <p>用于 DAG 条件分支节点（CONDITION）的表达式评估，
 * 支持从上下文中读取上游节点的执行结果进行条件判断。
 *
 * <p>表达式格式：{@code ${nodeA.result=='success'}} 或 {@code #nodeA.status!='FAILED'}
 * <ul>
 *   <li>支持 {@code ==} 和 {@code !=} 操作符</li>
 *   <li>支持字符串字面量（单引号或双引号）</li>
 *   <li>支持嵌套属性访问（如 {@code nodeA.result.code}）</li>
 *   <li>支持逻辑操作符 {@code &&} / {@code ||} / {@code !}</li>
 * </ul>
 *
 * <p>示例：
 * <ul>
 *   <li>{@code #nodeA.result=='success'} — 判断 nodeA 的结果是否为 success</li>
 *   <li>{@code #nodeA.status!='FAILED'} — 判断 nodeA 的状态是否非 FAILED</li>
 *   <li>{@code #nodeA.result.code==200 && #nodeB.status=='SUCCESS'} — 组合条件</li>
 * </ul>
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

        // 去除 ${...} 包裹（兼容 DAG 定义中的 ${expr} 格式）
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

    /**
     * 构建 SpEL 评估上下文。
     *
     * <p>将 Map 中的每个 key 注册为 SpEL 变量（#{#key}），
     * 同时注册 {@link MapAccessor} 支持 Map 属性的点号访问。
     */
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
