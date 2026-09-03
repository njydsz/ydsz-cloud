package com.njydsz.literule.server.engine.liteexpr;
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
      return new TraceNode(type, expression, null, null, result, false, 0, new ArrayList<>(16),
}
}
}