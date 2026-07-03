package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.api.RuleContext;

/**
 * 表达式求值器接口
 *
 * <p>抽象表达式引擎，支持 Aviator / QLExpress / SpEL 等多种实现。
 * 默认提供 {@link AviatorExpressionEvaluator}。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface ExpressionEvaluator {

    /**
     * 求值布尔表达式
     *
     * @param expression 表达式字符串
     * @param context    规则上下文
     * @return 表达式结果；求值异常返回 false
     */
    boolean evalBoolean(String expression, RuleContext context);

    /**
     * 求值表达式（通用类型）
     *
     * @param expression 表达式字符串
     * @param context    规则上下文
     * @param <T>        返回类型
     * @return 表达式结果
     */
    <T> T eval(String expression, RuleContext context);

    /**
     * 校验表达式语法是否合法
     *
     * @param expression 表达式字符串
     * @return true=合法；false=非法
     */
    boolean validate(String expression);

    /**
     * 详细校验表达式语法（1.4.0 起支持）
     *
     * <p>与 {@link #validate(String)} 不同，本方法返回结构化的错误信息，
     * 包含错误类型、错误位置、错误描述，供前端表达式编辑器渲染。
     *
     * <p>默认实现仅调用 {@link #validate(String)} 返回简单结果；
     * 具体实现类（如 {@link AviatorExpressionEvaluator}）应 override 本方法提供详细错误信息。
     *
     * @param expression 表达式字符串
     * @return 校验结果
     * @since 1.4.0
     */
    default ExpressionValidationResult validateDetailed(String expression) {
        long start = System.nanoTime();
        boolean ok = validate(expression);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        if (ok) {
            return ExpressionValidationResult.ok(expression, elapsed, java.util.List.of());
        }
        return ExpressionValidationResult.fail(expression,
                ExpressionValidationResult.ErrorType.UNKNOWN,
                "表达式非法", elapsed);
    }
}
