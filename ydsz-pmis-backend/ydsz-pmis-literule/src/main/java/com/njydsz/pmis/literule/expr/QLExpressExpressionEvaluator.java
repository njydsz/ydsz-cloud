package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.api.RuleContext;
import lombok.extern.slf4j.Slf4j;

/**
 * QLExpress 表达式求值器实现（骨架）
 *
 * <p>QLExpress 是阿里巴巴开源的轻量级表达式引擎，与 Aviator 相比：
 * <ul>
 *   <li>语法更接近 Java，学习成本低</li>
 *   <li>支持 if/else、for 等控制流</li>
 *   <li>执行效率略低，但功能更强</li>
 * </ul>
 *
 * <p>当前为骨架实现，所有方法抛出 {@link UnsupportedOperationException}。
 * 计划在 v2.0 引入 QLExpress 依赖后补全实现，通过
 * {@code pmis.literule.evaluator=aviator|qlexpress} 切换。
 *
 * <p>切换示例（计划）：
 * <pre>{@code
 * # application.yml
 * pmis:
 *   literule:
 *     evaluator: qlexpress   # 默认 aviator
 * }</pre>
 *
 * <p>当前阶段调用任何方法都会抛出异常，提示用户使用 {@link AviatorExpressionEvaluator}。
 * 待 v2.0 引入 {@code com.alibaba:QLExpress} 依赖后补全实现：
 * <ul>
 *   <li>编译缓存：参考 {@link AviatorExpressionEvaluator#compile} 的 {@code ConcurrentHashMap} 方案</li>
 *   <li>沙箱模式：限制 {@code addFunction} / 反射访问，参考 {@link AviatorExpressionEvaluator#configureSandbox}</li>
 *   <li>详细校验：override {@link #validateDetailed} 提供 QLExpress 语法错误行列号</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class QLExpressExpressionEvaluator implements ExpressionEvaluator {

    /** 切换到 QLExpress 求值器时的提示信息 */
    private static final String NOT_IMPLEMENTED_MSG =
            "QLExpress 求值器尚未实现，请使用 AviatorExpressionEvaluator";

    // TODO: 待 v2.0 引入 com.alibaba:QLExpress 依赖后实现以下方法

    @Override
    public boolean evalBoolean(String expression, RuleContext context) {
        // TODO: 使用 ExpressRunner.execute 编译并求值，参考 AviatorExpressionEvaluator#evalBoolean
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public <T> T eval(String expression, RuleContext context) {
        // TODO: 使用 ExpressRunner.execute 编译并求值，参考 AviatorExpressionEvaluator#eval
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public boolean validate(String expression) {
        // TODO: 使用 ExpressRunner.parseInstructionSet 静态校验语法
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public ExpressionValidationResult validateDetailed(String expression) {
        // TODO: 解析 QLExpress 异常中的行列号，组装 ExpressionValidationResult
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }
}
