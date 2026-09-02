package com.njydsz.literule.server.engine.liteexpr.ExprTraceBuilder;

import java.util.List;

/**
 * LiteExpr 表达式执行追踪树构建器
 *
 * <p>在 {@link TreeInterpreter} 求值过程中同步构建追踪树， 用于规则归因分析、短路排查和中间结果可视化。
 *
 * <p>追踪树结构示例：
 *
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
 * @since 26.09.01
 * @author ydsz-team
 */
public class ExprTraceBuilder {

  /**
   * 追踪节点（简化版，映射到 {@link com.njydsz.literule.server.engine.liteexpr.ExpressionTraceNode}）。
   *
   * @param type 节点类型（如 VARIABLE / LITERAL / BINARY_OP）
   * @param expression 原始表达式片段
   * @param operator 运算符（二元运算时非空）
   * @param value 求值前的操作数值
   * @param result 求值结果
   * @param shortCircuited 是否短路
   * @param elapsedNanos 节点耗时（纳秒）
   * @param children 子节点列表
   * @param error 错误信息（出错时非空）
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
      String error) {
    /**
     * 创建无子节点的叶子 TraceNode。
     *
     * @param type 节点类型（如 VARIABLE / LITERAL / BINARY_OP）
     * @param expression 原始表达式文本
     * @param result 该节点的求值结果
     * @return 叶子追踪节点实例
     */
    public static TraceNode of(String type, String expression, Object result) {
      return new TraceNode(type, expression, null, null, result, false, 0, new ArrayList<>(), null);
    }
  }

  private final List<TraceNode> nodes = new ArrayList<>(4);

  /**
   * 记录一个变量叶子节点（如 {@code amount}）。
   *
   * <p>仅将变量名与求值结果作为叶节点追加到当前追踪列表，不清除已有节点， 因此多个变量节点会依次累积，供后续逻辑/二元等父节点收纳为子树。 非线程安全：同一 {@link
   * ExprTraceBuilder} 不应跨线程并发使用。
   *
   * @param name 变量名
   * @param value 变量的求值结果（可空）
   */
  public void recordVariable(String name, Object value) {
    nodes.add(TraceNode.of("VARIABLE", name, value));
  }

  /**
   * 记录逻辑运算节点（AND / OR）并收束当前追踪列表。
   *
   * <p>将之前累积的子节点整体作为本节点的 {@code children}， 然后清空当前列表、仅保留本逻辑节点（模式：累积子节点 → 汇总为单节点）。 {@code
   * shortCircuited=true} 表示从该项起短路，不再继续求值。
   *
   * @param operator 逻辑运算符（AND / OR）
   * @param result 逻辑运算结果
   * @param shortCircuited 是否发生短路
   * @param node 对应 AST 节点（用于提取表达式文本）
   */
  public void recordLogical(
      String operator, boolean result, boolean shortCircuited, BinaryOpNode node) {
    TraceNode tn =
        new TraceNode(
            "LOGICAL",
            node.exprText(),
            operator,
            null,
            result,
            shortCircuited,
            0,
            new ArrayList<>(nodes),
            null);
    nodes.clear();
    nodes.add(tn);
  }

  /**
   * 记录二元运算节点（比较 / 算术）并收束当前追踪列表。
   *
   * <p>比较类（如 {@code >}、{@code ==}）标记为 {@code COMPARISON}，其余标记为 {@code ARITHMETIC}。
   * 与逻辑节点一致：先以当前累积子节点作为 children，再清空列表仅保留本节点。
   *
   * @param operator 运算符
   * @param left 左操作数（未直接使用，结果来自 node 求值）
   * @param right 右操作数
   * @param result 运算结果
   * @param node 对应 AST 节点（用于提取表达式文本与判断比较/算术）
   */
  public void recordBinary(
      String operator, Object left, Object right, Object result, BinaryOpNode node) {
    TraceNode tn =
        new TraceNode(
            node.isComparison() ? "COMPARISON" : "ARITHMETIC",
            node.exprText(),
            operator,
            null,
            result,
            false,
            0,
            new ArrayList<>(nodes),
            null);
    nodes.clear();
    nodes.add(tn);
  }

  /**
   * 记录一元运算节点（取反 / 负号等）并收束当前追踪列表。
   *
   * @param operator 运算符
   * @param operand 操作数
   * @param result 运算结果
   * @param node 对应 AST 节点（用于提取表达式文本）
   */
  public void recordUnary(String operator, Object operand, Object result, UnaryOpNode node) {
    TraceNode tn =
        new TraceNode(
            "UNARY",
            node.exprText(),
            operator,
            null,
            result,
            false,
            0,
            new ArrayList<>(nodes),
            null);
    nodes.clear();
    nodes.add(tn);
  }

  /**
   * 记录三元条件节点（{@code cond ? a : b}）并收束当前追踪列表。
   *
   * @param cond 条件求值结果
   * @param result 最终选中分支的结果
   * @param node 对应 AST 节点（用于提取表达式文本）
   */
  public void recordTernary(boolean cond, Object result, TernaryNode node) {
    TraceNode tn =
        new TraceNode(
            "TERNARY", node.exprText(), null, cond, result, false, 0, new ArrayList<>(nodes), null);
    nodes.clear();
    nodes.add(tn);
  }

  /**
   * 记录函数调用节点并收束当前追踪列表。
   *
   * @param name 函数名
   * @param args 实参数组（未展开记录，仅用于上下文）
   * @param result 函数返回值
   * @param node 对应 AST 节点（用于提取表达式文本）
   */
  public void recordFunctionCall(String name, Object[] args, Object result, FunctionCallNode node) {
    TraceNode tn =
        new TraceNode(
            "FUNCTION_CALL",
            node.exprText(),
            null,
            null,
            result,
            false,
            0,
            new ArrayList<>(nodes),
            null);
    nodes.clear();
    nodes.add(tn);
  }

  /**
   * 记录成员访问节点（{@code obj.member}）并收束当前追踪列表。
   *
   * @param target 访问目标对象
   * @param member 被访问的成员名
   * @param result 访问结果
   * @param node 对应 AST 节点（用于提取表达式文本）
   */
  public void recordMemberAccess(
      Object target, String member, Object result, MemberAccessNode node) {
    TraceNode tn =
        new TraceNode(
            "MEMBER_ACCESS",
            node.exprText(),
            null,
            null,
            result,
            false,
            0,
            new ArrayList<>(nodes),
            null);
    nodes.clear();
    nodes.add(tn);
  }

  /**
   * 构建根追踪节点
   *
   * @param ast 抽象语法树根节点
   * @param result 表达式求值结果
   * @return 根追踪节点（包含所有子节点的追踪信息）
   */
  public TraceNode buildRoot(ExprNode ast, Object result) {
    if (nodes.size() == 1) {
      return nodes.get(0);
    }
    return new TraceNode(
        "ROOT", ast.exprText(), null, null, result, false, 0, new ArrayList<>(nodes), null);
  }
}
