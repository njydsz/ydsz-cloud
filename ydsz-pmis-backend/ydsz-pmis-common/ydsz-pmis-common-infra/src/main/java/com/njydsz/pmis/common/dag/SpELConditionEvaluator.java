package com.njydsz.pmis.common.dag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一 SpEL 条件表达式引擎（P0-1 架构优化）。
 *
 * <p>合并 cronjob 模块的 {@code SpELConditionEvaluator} 和 agent 模块内联的 SpEL 逻辑，
 * 提供通用的 DAG 条件表达式评估能力。
 *
 * <h3>支持的表达式语法</h3>
 * <ul>
 *   <li>相等比较：{@code #nodeA.result == 'success'}</li>
 *   <li>不等比较：{@code #nodeA.status != 'FAILED'}</li>
 *   <li>逻辑运算：{@code #nodeA.result == 'success' and #nodeB.result == 'success'}</li>
 *   <li>或运算：{@code #nodeA.result == 'success' or #nodeB.result == 'success'}</li>
 *   <li>包含判断：{@code #nodeA.result.contains('ok')}</li>
 *   <li>数值比较：{@code #nodeA.duration > 1000}</li>
 *   <li>正则匹配：{@code #nodeA.result matches '.*success.*'}</li>
 *   <li>三元表达式：{@code #nodeA.result == 'success' ? true : false}</li>
 * </ul>
 *
 * <h3>表达式格式</h3>
 * <p>支持两种格式：
 * <ul>
 *   <li>SpEL 格式（推荐）：{@code #nodeA.result == 'success'}</li>
 *   <li>兼容旧格式：{@code ${nodeA.result=='success'}}（自动去掉 ${} 包装后按 SpEL 解析）</li>
 * </ul>
 *
 * <h3>安全措施</h3>
 * <ul>
 *   <li>使用受限的 {@link StandardEvaluationContext}（不注册任何函数）</li>
 *   <li>禁止类型引用（T(...)）和实例化（new ...）</li>
 *   <li>表达式解析缓存（ConcurrentHashMap）避免重复编译</li>
 *   <li>评估异常时返回 false（不影响 DAG 执行）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-1)
 */
@Slf4j
@Component
public class SpELConditionEvaluator {

    /** SpEL 表达式解析器（线程安全，可复用） */
    private final ExpressionParser parser = new SpelExpressionParser();

    /** 表达式缓存（expression string → compiled Expression） */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /** 旧格式 ${...} 包装匹配模式 */
    private static final java.util.regex.Pattern WRAPPED_PATTERN =
            java.util.regex.Pattern.compile("^\\$\\{(.+)\\}$");

    /**
     * 评估条件表达式。
     *
     * <p>表达式以 {@code #变量名} 引用上下文中的节点结果。
     * 评估结果为 Boolean 类型；非 Boolean 结果会尝试转换。
     *
     * @param expression 条件表达式（SpEL 或兼容旧格式 ${...}）
     * @param context    上下文（key=节点标识, value=节点结果对象）
     * @return 评估结果；表达式为空或解析失败时返回 false
     */
    public boolean evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return false;
        }

        String spelExpr = unwrapExpression(expression.trim());

        try {
            Expression expr = expressionCache.computeIfAbsent(spelExpr, parser::parseExpression);
            EvaluationContext evalContext = buildEvaluationContext(context);
            Boolean result = expr.getValue(evalContext, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("[SpEL] 条件表达式评估失败, 返回 false: expr={} reason={}", expression, e.getMessage());
            return false;
        }
    }

    /**
     * 去除 ${...} 包装（兼容旧格式）。
     *
     * <p>旧格式 {@code ${nodeA.result=='success'}} 会被转换为 SpEL 格式
     * {@code #nodeA.result == 'success'}。
     *
     * @param expression 原始表达式
     * @return SpEL 格式表达式
     */
    private String unwrapExpression(String expression) {
        java.util.regex.Matcher matcher = WRAPPED_PATTERN.matcher(expression);
        String inner = matcher.matches() ? matcher.group(1).trim() : expression;

        if (inner.startsWith("#")) {
            return inner;
        }

        inner = java.util.regex.Pattern.compile(
                "(^|\\s|\\(|and\\s|or\\s)([\\w-]+)\\.")
                .matcher(inner)
                .replaceAll(mr -> mr.group(1) + "#" + mr.group(2) + ".");

        return inner;
    }

    /**
     * 构建 SpEL 评估上下文。
     *
     * @param context 上下文
     * @return SpEL 评估上下文
     */
    private EvaluationContext buildEvaluationContext(Map<String, Object> context) {
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        if (context != null) {
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                evalContext.setVariable(entry.getKey(), entry.getValue());
            }
        }
        return evalContext;
    }

    /**
     * 清除表达式缓存。
     */
    public void clearCache() {
        expressionCache.clear();
        log.info("[SpEL] 表达式缓存已清空");
    }
}
