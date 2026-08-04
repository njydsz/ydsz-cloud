package com.remisoft.literule.api.expr;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表达式校验结果
 *
 * <p>由 {@link ExpressionValidationService} 在表达式保存前/编辑时校验返回，
 * 提供给前端表达式编辑器渲染错误位置、错误类型、修复建议等。
 *
 * <p>当 {@link #valid} 为 false 时，{@link #errorType} 与 {@link #errorMessage} 必填。
 * 当 {@link #valid} 为 true 时，{@link #errorType} 为 {@link ErrorType#OK}。
 *
 * @since 1.0.0
 * @author remi-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpressionValidationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错误类型枚举 */
    public enum ErrorType {
        /** 校验通过 */
        OK,
        /** 表达式为空或全为空白字符 */
        EMPTY,
        /** LiteExpr 语法错误（缺括号、运算符错误等） */
        SYNTAX_ERROR,
        /** 沙箱拦截（包含危险函数或类访问） */
        SANDBOX_VIOLATION,
        /** 引用了未定义的变量（依赖 VariableRegistry，当前未启用） */
        UNDEFINED_VARIABLE,
        /** 模板占位符未闭合（如 ${foo 缺 } ） */
        TEMPLATE_FORMAT_ERROR,
        /** 其他未知错误 */
        UNKNOWN
    }

    /** 校验是否通过 */
    private boolean valid;

    /** 错误类型；valid=true 时为 {@link ErrorType#OK} */
    private ErrorType errorType;

    /** 错误描述（前端可直接展示给用户） */
    private String errorMessage;

    /** 错误所在行（1-based，无法定位时为 -1） */
    private int errorLine;

    /** 错误所在列（1-based，无法定位时为 -1） */
    private int errorColumn;

    /** 原始表达式（用于前端高亮） */
    private String expression;

    /** 校验耗时（毫秒） */
    private long parseTimeMs;

    /**
     * 表达式中引用的变量列表（提取自表达式，不依赖 VariableRegistry）
     *
     * <p>用于前端编辑器的"已使用变量"提示，不参与合法性判断。
     * 当 VariableRegistry（P2-4）落地后，这里会替换为"已使用 vs 已定义"对比结果。
     */
    @Builder.Default
    private List<String> referencedVariables = new ArrayList<>();

    /**
     * 快速构造合法结果
     *
     * @param expression 表达式
     * @param parseTimeMs 校验耗时
     * @param referencedVariables 引用变量列表
     * @return 合法结果
     */
    public static ExpressionValidationResult ok(String expression, long parseTimeMs,
                                                 List<String> referencedVariables) {
        return ExpressionValidationResult.builder()
                .valid(true)
                .errorType(ErrorType.OK)
                .errorMessage(null)
                .errorLine(-1)
                .errorColumn(-1)
                .expression(expression)
                .parseTimeMs(parseTimeMs)
                .referencedVariables(referencedVariables != null ? referencedVariables : new ArrayList<>())
                .build();
    }

    /**
     * 快速构造非法结果
     *
     * @param expression 表达式
     * @param errorType 错误类型
     * @param errorMessage 错误描述
     * @param parseTimeMs 校验耗时
     * @return 非法结果
     */
    public static ExpressionValidationResult fail(String expression, ErrorType errorType,
                                                   String errorMessage, long parseTimeMs) {
        return ExpressionValidationResult.builder()
                .valid(false)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .errorLine(-1)
                .errorColumn(-1)
                .expression(expression)
                .parseTimeMs(parseTimeMs)
                .referencedVariables(new ArrayList<>())
                .build();
    }
}
