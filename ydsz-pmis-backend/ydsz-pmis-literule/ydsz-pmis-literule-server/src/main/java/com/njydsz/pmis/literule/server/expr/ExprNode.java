package com.njydsz.pmis.literule.server.expr.liteexpr;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * LiteExpr AST 节点体系
 *
 * <p>所有 AST 节点实现 {@link ExprNode} 接口，支持 {@link ExprNodeVisitor} 访问者模式。
 * 节点携带行列位置信息，用于错误定位和追踪树可视化。
 *
 * <p>节点类型：
 * <ul>
 *   <li>{@link LiteralNode}     — 字面值（数字/字符串/布尔/null）</li>
 *   <li>{@link VariableNode}    — 变量引用</li>
 *   <li>{@link BinaryOpNode}    — 二元运算（算术/比较/逻辑）</li>
 *   <li>{@link UnaryOpNode}     — 一元运算（! / -）</li>
 *   <li>{@link TernaryNode}     — 三元条件 (cond ? a : b)</li>
 *   <li>{@link FunctionCallNode}— 函数调用</li>
 *   <li>{@link MemberAccessNode}— 属性访问 a.b.c</li>
 *   <li>{@link IndexNode}       — 索引 a[0] / map["key"]</li>
 *   <li>{@link ListNode}        — 列表 [1, 2, 3]</li>
 *   <li>{@link MapNode}         — 字典 {k: v}</li>
 *   <li>{@link LambdaNode}      — Lambda x -> x > 100</li>
 *   <li>{@link TemplateStringNode} — 模板字符串 `Hello ${name}`</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */

// ===== AST 节点接口 =====

sealed interface ExprNode permits
        LiteralNode, VariableNode, BinaryOpNode, UnaryOpNode,
        TernaryNode, FunctionCallNode, MemberAccessNode,
        IndexNode, ListNode, MapNode, LambdaNode, TemplateStringNode {

    /**
     * 接受 Visitor
     */
    <R> R accept(ExprNodeVisitor<R> visitor);

    /** 节点起始行（1-based） */
    int line();

    /** 节点起始列（1-based） */
    int column();

    /**
     * 节点对应的表达式文本（用于追踪树展示）
     */
    String exprText();
}

// ===== AST Visitor 接口 =====

/**
 * AST 访问者接口
 *
 * @param <R> 返回类型
 */
interface ExprNodeVisitor<R> {
    R visitLiteral(LiteralNode node);
    R visitVariable(VariableNode node);
    R visitBinaryOp(BinaryOpNode node);
    R visitUnaryOp(UnaryOpNode node);
    R visitTernary(TernaryNode node);
    R visitFunctionCall(FunctionCallNode node);
    R visitMemberAccess(MemberAccessNode node);
    R visitIndex(IndexNode node);
    R visitList(ListNode node);
    R visitMap(MapNode node);
    R visitLambda(LambdaNode node);
    R visitTemplateString(TemplateStringNode node);
}

// ===== 具体节点实现 =====

/**
 * 字面值节点
 */
record LiteralNode(
        Object value,
        int line,
        int column,
        String exprText
) implements ExprNode {
    public LiteralNode(Object value, int line, int column) {
        this(value, line, column, String.valueOf(value));
    }

    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitLiteral(this);
    }
}

/**
 * 变量引用节点
 */
record VariableNode(
        String name,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitVariable(this);
    }

    @Override
    public String exprText() {
        return name;
    }
}

/**
 * 二元运算节点
 *
 * @param operator 运算符（+ - * / % == != > >= < <= && ||）
 * @param left     左操作数
 * @param right    右操作数
 */
record BinaryOpNode(
        String operator,
        ExprNode left,
        ExprNode right,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitBinaryOp(this);
    }

    @Override
    public String exprText() {
        return left.exprText() + " " + operator + " " + right.exprText();
    }

    /**
     * 是否为逻辑运算（&& / ||）
     */
    public boolean isLogical() {
        return "&&".equals(operator) || "||".equals(operator) || "and".equals(operator) || "or".equals(operator);
    }

    /**
     * 是否为比较运算（== != > >= < <=）
     */
    public boolean isComparison() {
        return switch (operator) {
            case "==", "!=", ">", ">=", "<", "<=" -> true;
            default -> false;
        };
    }

    /**
     * 是否为算术运算（+ - * / %）
     */
    public boolean isArithmetic() {
        return switch (operator) {
            case "+", "-", "*", "/", "%" -> true;
            default -> false;
        };
    }
}

/**
 * 一元运算节点
 *
 * @param operator 运算符（! / -）
 * @param operand  操作数
 */
record UnaryOpNode(
        String operator,
        ExprNode operand,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitUnaryOp(this);
    }

    @Override
    public String exprText() {
        return operator + operand.exprText();
    }
}

/**
 * 三元条件节点 (cond ? thenExpr : elseExpr)
 */
record TernaryNode(
        ExprNode condition,
        ExprNode thenExpr,
        ExprNode elseExpr,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitTernary(this);
    }

    @Override
    public String exprText() {
        return condition.exprText() + " ? " + thenExpr.exprText() + " : " + elseExpr.exprText();
    }
}

/**
 * 函数调用节点
 *
 * @param functionName 函数名
 * @param arguments    参数列表
 */
record FunctionCallNode(
        String functionName,
        List<ExprNode> arguments,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitFunctionCall(this);
    }

    @Override
    public String exprText() {
        return functionName + "(" + arguments.stream().map(ExprNode::exprText).reduce((a, b) -> a + ", " + b).orElse("") + ")";
    }
}

/**
 * 属性访问节点 a.b.c
 *
 * @param target  被访问的对象（变量或嵌套表达式）
 * @param member  属性名
 */
record MemberAccessNode(
        ExprNode target,
        String member,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitMemberAccess(this);
    }

    @Override
    public String exprText() {
        return target.exprText() + "." + member;
    }

    /**
     * 提取完整的属性链（如 a.b.c → ["a", "b", "c"]）
     */
    public List<String> memberChain() {
        ArrayList<String> chain = new ArrayList<>();
        ExprNode current = this;
        while (current instanceof MemberAccessNode man) {
            chain.add(0, man.member());
            current = man.target();
        }
        if (current instanceof VariableNode vn) {
            chain.add(0, vn.name());
        }
        return chain;
    }
}

/**
 * 索引访问节点 a[0] / map["key"]
 *
 * @param target 被索引的对象
 * @param index  索引表达式
 */
record IndexNode(
        ExprNode target,
        ExprNode index,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitIndex(this);
    }

    @Override
    public String exprText() {
        return target.exprText() + "[" + index.exprText() + "]";
    }
}

/**
 * 列表字面量节点 [1, 2, 3]
 */
record ListNode(
        List<ExprNode> elements,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitList(this);
    }

    @Override
    public String exprText() {
        return "[" + elements.stream().map(ExprNode::exprText).reduce((a, b) -> a + ", " + b).orElse("") + "]";
    }
}

/**
 * 字典字面量节点 {key: value, ...}
 */
record MapNode(
        Map<ExprNode, ExprNode> entries,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitMap(this);
    }

    @Override
    public String exprText() {
        return "{" + entries.entrySet().stream()
                .map(e -> e.getKey().exprText() + ": " + e.getValue().exprText())
                .reduce((a, b) -> a + ", " + b).orElse("") + "}";
    }
}

/**
 * Lambda 节点 x -> x > 100
 *
 * @param parameter Lambda 参数名
 * @param body       Lambda 体
 */
record LambdaNode(
        String parameter,
        ExprNode body,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitLambda(this);
    }

    @Override
    public String exprText() {
        return parameter + " -> " + body.exprText();
    }
}

/**
 * 模板字符串节点 `Hello ${name}!`
 *
 * @param parts 模板片段列表（LiteralNode 为字符串部分，其他为表达式部分）
 */
record TemplateStringNode(
        List<ExprNode> parts,
        int line,
        int column
) implements ExprNode {
    @Override
    public <R> R accept(ExprNodeVisitor<R> visitor) {
        return visitor.visitTemplateString(this);
    }

    @Override
    public String exprText() {
        StringBuilder sb = new StringBuilder("`");
        for (ExprNode part : parts) {
            if (part instanceof LiteralNode ln) {
                sb.append(ln.value());
            } else {
                sb.append("${").append(part.exprText()).append("}");
            }
        }
        sb.append("`");
        return sb.toString();
    }
}
