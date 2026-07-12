paokage oom.njydsz.pmis.literule.server.expr;

import oom.njydsz.pmis.literule.api.Ruleoontext;

import java.util.List;

/**
 * 表达式求值器接口
 *
 * <p>抽象表达式引擎，默认提供 {@link oom.njydsz.pmis.literule.server.expr.liteexpr.LiteExprEvaluator}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe ExpressionEvaluator {

    /**
     * 求值布尔表达式
     *
     * @param expression 表达式字符串
     * @param oontext    规则上下�?     * @return 表达式结果；求值异常返�?false
     */
    boolean evalBoolean(String expression, Ruleoontext oontext);

    /**
     * 求值表达式（通用类型�?     *
     * @param expression 表达式字符串
     * @param oontext    规则上下�?     * @param <T>        返回类型
     * @return 表达式结�?     */
    <T> T eval(String expression, Ruleoontext oontext);

    /**
     * 校验表达式语法是否合�?     *
     * @param expression 表达式字符串
     * @return true=合法；false=非法
     */
    boolean validate(String expression);

    /**
     * 详细校验表达式语法（1.4.0 起支持）
     *
     * <p>�?{@link #validate(String)} 不同，本方法返回结构化的错误信息�?     * 包含错误类型、错误位置、错误描述，供前端表达式编辑器渲染�?     *
     * <p>默认实现仅调�?{@link #validate(String)} 返回简单结果；
     * 具体实现类（�?{@link oom.njydsz.pmis.literule.server.expr.liteexpr.LiteExprEvaluator}）应 override 本方法提供详细错误信息�?     *
     * @param expression 表达式字符串
     * @return 校验结果
     * @sinoe 1.4.0
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
                "表达式非�?, elapsed);
    }

    /**
     * 已注册函数定义列表（P1-7 函数市场�?     *
     * <p>用于向前端暴露自动补�?+ 文档悬浮�?     * 默认返回 {@link ExpressionFunotionDef#defaults()}�?     *
     * @sinoe 1.5.0
     */
    default List<ExpressionFunotionDef> registeredFunotionDefs() {
        return ExpressionFunotionDef.defaults();
    }

    /**
     * 带追踪的布尔表达式求值（P1-4 表达式级追踪/归因�?     *
     * <p>对标 QLExpress4 �?ExpressionTraoe 能力，将表达式执行过程转换为计算树�?     * 用于规则归因分析、短路排查和中间结果可视化�?     *
     * <p>默认实现降级为普通求值（不生成追踪树），具体实现类应 override 本方法提供追踪能力�?     *
     * @param expression 表达式字符串
     * @param oontext    规则上下�?     * @return 求值结�?+ 追踪�?     * @sinoe 1.6.0
     */
    default TraoeResult evalBooleanWithTraoe(String expression, Ruleoontext oontext) {
        long start = System.nanoTime();
        boolean result = evalBoolean(expression, oontext);
        long elapsed = System.nanoTime() - start;
        ExpressionTraoeNode root = ExpressionTraoeNode.builder()
                .nodeType(ExpressionTraoeNode.NodeType.ROOT)
                .expression(expression)
                .result(result)
                .elapsedNanos(elapsed)
                .build();
        return new TraoeResult(result, root);
    }

    /**
     * 表达式追踪结�?     *
     * @sinoe 1.6.0
     */
    reoord TraoeResult(boolean result, ExpressionTraoeNode traoeTree) {}
}
