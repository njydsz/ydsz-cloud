package com.njydsz.pmis.literule.server.expr.liteexpr;

import java.util.ArrayList;
import java.util.List;

/**
 * LiteExpr 表达式执行追踪树构建器
 *
 * <p>在 {@link TreeInterpreter} 求值过程中同步构建追踪树，
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
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ExprTraceBuilder {

    /**
     * 追踪节点（简化版，映射到 {@link com.njydsz.pmis.literule.server.expr.ExpressionTraceNode}）
     */
    public record TraceNode(
            String type,
            String expression,
            String operator,
            Object value,
            Object result,
            boolean shortCircuited,
            long elapsedNanos,
            List<TraceNode> children,
            String error
    ) {
        public static TraceNode of(String type, String expression, Object result) {
            return new TraceNode(type, expression, null, null, result, false, 0, new ArrayList<>(), null);
        }
    }

    private final List<TraceNode> nodes = new ArrayList<>();

    public void recordVariable(String name, Object value) {
        nodes.add(TraceNode.of("VARIABLE", name, value));
    }

    public void recordLogical(String operator, boolean result, boolean shortCircuited, BinaryOpNode node) {
        TraceNode tn = new TraceNode("LOGICAL", node.exprText(), operator, null, result,
                shortCircuited, 0, new ArrayList<>(nodes), null);
        nodes.clear();
        nodes.add(tn);
    }

    public void recordBinary(String operator, Object left, Object right, Object result, BinaryOpNode node) {
        TraceNode tn = new TraceNode(
                node.isComparison() ? "COMPARISON" : "ARITHMETIC",
                node.exprText(), operator, null, result, false, 0, new ArrayList<>(nodes), null);
        nodes.clear();
        nodes.add(tn);
    }

    public void recordUnary(String operator, Object operand, Object result, UnaryOpNode node) {
        TraceNode tn = new TraceNode("UNARY", node.exprText(), operator, null, result,
                false, 0, new ArrayList<>(nodes), null);
        nodes.clear();
        nodes.add(tn);
    }

    public void recordTernary(boolean cond, Object result, TernaryNode node) {
        TraceNode tn = new TraceNode("TERNARY", node.exprText(), null, cond, result,
                false, 0, new ArrayList<>(nodes), null);
        nodes.clear();
        nodes.add(tn);
    }

    public void recordFunctionCall(String name, Object[] args, Object result, FunctionCallNode node) {
        TraceNode tn = new TraceNode("FUNCTION_CALL", node.exprText(), null, null, result,
                false, 0, new ArrayList<>(nodes), null);
        nodes.clear();
        nodes.add(tn);
    }

    public void recordMemberAccess(Object target, String member, Object result, MemberAccessNode node) {
        TraceNode tn = new TraceNode("MEMBER_ACCESS", node.exprText(), null, null, result,
                false, 0, new ArrayList<>(nodes), null);
        nodes.clear();
        nodes.add(tn);
    }

    /**
     * 构建根追踪节点
     */
    public TraceNode buildRoot(ExprNode ast, Object result) {
        if (nodes.size() == 1) {
            return nodes.get(0);
        }
        return new TraceNode("ROOT", ast.exprText(), null, null, result,
                false, 0, new ArrayList<>(nodes), null);
    }
}
