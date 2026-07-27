package com.njydsz.literule.api.expr;

import java.util.List;

import com.njydsz.literule.api.RuleContext;

/**
 * 表达式求值器接口
 *
 * <p>抽象表达式引擎，默认提供 {@link com.njydsz.literule.server.expr.liteexpr.LiteExprEvaluator}。
 *
 * @since 1.0.0
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
     * <p>返回 {@link Object}，由调用方根据业务语义自行类型转换。
     * 早期版本使用 {@code <T> T eval(...)} 泛型签名，但表达式求值本质是动态类型，
     * 强制泛型化会引入 unchecked 转换；改为 {@code Object} 后类型转换显式化，符合"零警告"规范。
     *
     * @param expression 表达式字符串
     * @param context    规则上下文
     * @return 表达式结果；求值异常返回 null
     */
    Object eval(String expression, RuleContext context);

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
     * 具体实现类（如 {@link com.njydsz.literule.server.expr.liteexpr.LiteExprEvaluator}）应 override 本方法提供详细错误信息。
     *
     * @param expression 表达式字符串
     * @return 校验结果
     * @since 1.0.0
     */
    default ExpressionValidationResult validateDetailed(String expression) {
        long start = System.nanoTime();
        boolean ok = validate(expression);
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        if (ok) {
            return ExpressionValidationResult.ok(expression, elapsed, List.of());
        }
        return ExpressionValidationResult.fail(expression,
                ExpressionValidationResult.ErrorType.UNKNOWN,
                "表达式非法", elapsed);
    }

    /**
     * 已注册函数定义列表（P1-7 函数市场）
     *
     * <p>用于向前端暴露自动补全 + 文档悬浮。
     * 默认返回 {@link ExpressionFunctionDef#defaults()}。
     *
     * @since 1.0.0
     */
    default List<ExpressionFunctionDef> registeredFunctionDefs() {
        return ExpressionFunctionDef.defaults();
    }

    /**
     * 带追踪的布尔表达式求值（P1-4 表达式级追踪/归因）
     *
     * <p>对标 QLExpress4 的 ExpressionTrace 能力，将表达式执行过程转换为计算树。
     * 用于规则归因分析、短路排查和中间结果可视化。
     *
     * <p>默认实现降级为普通求值（不生成追踪树），具体实现类应 override 本方法提供追踪能力。
     *
     * @param expression 表达式字符串
     * @param context    规则上下文
     * @return 求值结果 + 追踪树
     * @since 1.0.0
     */
    default TraceResult evalBooleanWithTrace(String expression, RuleContext context) {
        long start = System.nanoTime();
        boolean result = evalBoolean(expression, context);
        long elapsed = System.nanoTime() - start;
        ExpressionTraceNode root = ExpressionTraceNode.builder()
                .nodeType(ExpressionTraceNode.NodeType.ROOT)
                .expression(expression)
                .result(result)
                .elapsedNanos(elapsed)
                .build();
        return new TraceResult(result, root);
    }

    /**
     * 表达式追踪结果
     *
     * @since 1.0.0
     */
    record TraceResult(boolean result, ExpressionTraceNode traceTree) {}
}
