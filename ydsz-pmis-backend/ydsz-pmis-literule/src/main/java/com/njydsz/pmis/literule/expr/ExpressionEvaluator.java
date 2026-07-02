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
}
