package com.njydsz.literule.server.expr;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表达式执行追踪节点（P1-4）
 *
 * <p>对标 QLExpress4 的 ExpressionTrace 能力，将表达式执行过程转换为可折叠计算树，
 * 用于规则归因分析、短路排查和中间结果可视化。
 *
 * <p>追踪树结构示例：
 * <pre>
 * AND(amount > 1000 && score > 800)
 * ├── Comparison(amount > 1000)
 * │   ├── Variable(amount) = 1500
 * │   └── Literal(1000)
 * │   └── Result = true
 * └── Comparison(score > 800)
 *     ├── Variable(score) = 750
 *     └── Literal(800)
 *     └── Result = false
 * └── Final = false (short-circuit at 2nd condition)
 * </pre>
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpressionTraceNode {

    /** 节点类型 */
    private NodeType nodeType;

    /** 表达式片段（如 "amount > 1000"） */
    private String expression;

    /** 变量名（VARIABLE 类型使用） */
    private String variableName;

    /** 变量值（VARIABLE 类型使用） */
    private Object variableValue;

    /** 字面值（LITERAL 类型使用） */
    private Object literalValue;

    /** 运算符（COMPARISON/LOGICAL 类型使用，如 ">" / "&&" / "||"） */
    private String operator;

    /** 本节点求值结果 */
    private Object result;

    /** 是否短路（AND 的右侧被跳过 / OR 的右侧被跳过） */
    private boolean shortCircuited;

    /** 执行耗时（纳秒） */
    private long elapsedNanos;

    /** 子节点（逻辑运算符的左右操作数、函数调用的参数等） */
    @Builder.Default
    private List<ExpressionTraceNode> children = new ArrayList<>();

    /** 错误信息（求值异常时填充） */
    private String error;

    /**
     * 节点类型枚举
     */
    public enum NodeType {
        /** 根表达式 */
        ROOT,
        /** 逻辑运算（&& / || / !） */
        LOGICAL,
        /** 比较运算（> / < / >= / <= / == / !=） */
        COMPARISON,
        /** 算术运算（+ / - / * / / / %） */
        ARITHMETIC,
        /** 变量引用 */
        VARIABLE,
        /** 字面值 */
        LITERAL,
        /** 函数调用 */
        FUNCTION_CALL,
        /** 三元运算 */
        TERNARY,
        /** 括号分组 */
        GROUP
    }

    /**
     * 快速构建变量节点
     */
    public static ExpressionTraceNode variable(String name, Object value) {
        return ExpressionTraceNode.builder()
                .nodeType(NodeType.VARIABLE)
                .variableName(name)
                .variableValue(value)
                .expression(name)
                .result(value)
                .build();
    }

    /**
     * 快速构建字面值节点
     */
    public static ExpressionTraceNode literal(Object value) {
        return ExpressionTraceNode.builder()
                .nodeType(NodeType.LITERAL)
                .literalValue(value)
                .expression(String.valueOf(value))
                .result(value)
                .build();
    }

    /**
     * 快速构建逻辑运算节点
     */
    public static ExpressionTraceNode logical(String operator, Object result, ExpressionTraceNode... children) {
        List<ExpressionTraceNode> childList = new ArrayList<>(List.of(children));
        String expr = childList.stream()
                .map(ExpressionTraceNode::getExpression)
                .reduce((a, b) -> a + " " + operator + " " + b)
                .orElse(operator);
        return ExpressionTraceNode.builder()
                .nodeType(NodeType.LOGICAL)
                .operator(operator)
                .expression(expr)
                .result(result)
                .children(childList)
                .build();
    }

    /**
     * 快速构建比较运算节点
     */
    public static ExpressionTraceNode comparison(String operator, String leftExpr, Object leftVal,
                                                  String rightExpr, Object rightVal, boolean result) {
        ExpressionTraceNode left = variable(leftExpr, leftVal);
        ExpressionTraceNode right = literal(rightVal);
        return ExpressionTraceNode.builder()
                .nodeType(NodeType.COMPARISON)
                .operator(operator)
                .expression(leftExpr + " " + operator + " " + rightExpr)
                .result(result)
                .children(List.of(left, right))
                .build();
    }
}
