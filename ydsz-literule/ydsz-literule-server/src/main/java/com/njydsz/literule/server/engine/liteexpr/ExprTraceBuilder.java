package com.njydsz.literule.server.engine.liteexpr;

import java.util.ArrayList;
import java.util.List;

/**
 * LiteExpr 表达式执行追踪树构建器
 *
 * <p>在 {@link TreeInterpreter} 求值过程中同步构建追踪树，用于规则归因分析、短路排查和中间结果可视化。
 *
 * <p>追踪树结构示例：
 *
 * <pre>
 * AND(amount &gt; 1000 &amp;&amp; score &gt; 800)
 * ├── Comparison(amount &gt; 1000)
 * │   ├── Variable(amount) = 1500
 * │   └── Literal(1000)
 * │   └── Result = true
 * └── Comparison(score &gt; 800)
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

  /** 根节点栈（支持嵌套表达式构建） */
  private final List<TraceNode> rootNodes = new ArrayList<>(4);

  /** 当前正在追加子节点的节点栈 */
  private final List<TraceNode> stack = new ArrayList<>(8);

  /**
   * 追踪节点
   *
   * @param type          节点类型（如 VARIABLE / LITERAL / BINARY_OP）
   * @param expression    原始表达式片段
   * @param operator      运算符（二元运算时非空）
   * @param value         求值前的操作数值
   * @param result        求值结果
   * @param shortCircuited 是否短路
   * @param elapsedNanos  节点耗时（纳秒）
   * @param children      子节点列表
   * @param error         错误信息（出错时非空）
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
     * @param type       节点类型（如 VARIABLE / LITERAL / BINARY_OP）
     * @param expression 原始表达式文本
     * @param result     该节点的求值结果
     * @return 叶子追踪节点实例
     */
    public static TraceNode of(String type, String expression, Object result) {
      return new TraceNode(type, expression, null, null, result, false, 0, new ArrayList<>(4), null);
    }

    /**
     * 创建带子节点的 TraceNode。
     *
     * @param type       节点类型
     * @param expression 原始表达式文本
     * @param result     求值结果
     * @param children   子节点列表
     * @return 追踪节点实例
     */
    public static TraceNode of(String type, String expression, Object result, List<TraceNode> children) {
      return new TraceNode(type, expression, null, null, result, false, 0, children, null);
    }

    /**
     * 创建错误追踪节点。
     *
     * @param type       节点类型
     * @param expression 原始表达式文本
     * @param error      错误描述
     * @return 错误追踪节点实例
     */
    public static TraceNode error(String type, String expression, String error) {
      return new TraceNode(type, expression, null, null, null, false, 0, new ArrayList<>(4), error);
    }

    /**
     * 添加子节点（返回 this 方便链式不可变操作时生成新节点）。
     *
     * @param child 子节点
     * @return 包含新子节点的 TraceNode 副本
     */
    public TraceNode withChild(TraceNode child) {
      List<TraceNode> newChildren = new ArrayList<>(this.children);
      newChildren.add(child);
      return new TraceNode(type, expression, operator, value, result, shortCircuited, elapsedNanos, newChildren, error);
    }

    /**
     * 创建有返回值的叶子 TraceNode。
     *
     * @param type     节点类型
     * @param value    输入值
     * @param result   结果值
     * @return 叶子节点
     */
    public static TraceNode leaf(String type, Object value, Object result) {
      return new TraceNode(type, null, null, value, result, false, 0, new ArrayList<>(4), null);
    }
  }

  /**
   * 开始追踪一个根节点。
   *
   * @param type       节点类型
   * @param expression 表达式文本
   */
  public void beginRoot(String type, String expression) {
    TraceNode node = TraceNode.of(type, expression, null);
    rootNodes.add(node);
    stack.clear();
    stack.add(node);
  }

  /**
   * 开始追踪一个子节点（作为当前栈顶节点的子节点入栈）。
   *
   * @param type       节点类型
   * @param expression 表达式片段
   */
  public void beginChild(String type, String expression) {
    if (stack.isEmpty()) {
      beginRoot(type, expression);
      return;
    }
    TraceNode child = TraceNode.of(type, expression, null);
    TraceNode parent = stack.get(stack.size() - 1);
    TraceNode updated = parent.withChild(child);
    // 替换栈顶为更新后的父节点
    stack.set(stack.size() - 1, updated);
    // 同步替换根节点（如果是根节点）
    if (stack.size() == 1) {
      rootNodes.set(rootNodes.size() - 1, updated);
    }
    stack.add(child);
  }

  /**
   * 结束当前节点（记录结果并退栈）。
   *
   * @param result 求值结果
   */
  public void endCurrent(Object result) {
    if (stack.isEmpty()) {
      return;
    }
    int lastIdx = stack.size() - 1;
    TraceNode current = stack.get(lastIdx);
    TraceNode completed = new TraceNode(
        current.type(),
        current.expression(),
        current.operator(),
        current.value(),
        result,
        current.shortCircuited(),
        current.elapsedNanos(),
        current.children(),
        current.error());
    stack.remove(lastIdx);

    // 更新父节点中的引用
    if (!stack.isEmpty()) {
      int parentIdx = stack.size() - 1;
      TraceNode parent = stack.get(parentIdx);
      List<TraceNode> newChildren = new ArrayList<>(parent.children());
      // 替换最后一个子节点（就是刚完成的 current）
      if (!newChildren.isEmpty()) {
        newChildren.set(newChildren.size() - 1, completed);
      }
      TraceNode updatedParent = new TraceNode(
          parent.type(), parent.expression(), parent.operator(), parent.value(),
          parent.result(), parent.shortCircuited(), parent.elapsedNanos(),
          newChildren, parent.error());
      stack.set(parentIdx, updatedParent);
      if (stack.size() == 1) {
        rootNodes.set(rootNodes.size() - 1, updatedParent);
      }
    } else {
      // 是当前根节点
      rootNodes.set(rootNodes.size() - 1, completed);
    }
  }

  /**
   * 标记当前节点为短路。
   */
  public void markShortCircuited() {
    if (stack.isEmpty()) {
      return;
    }
    int lastIdx = stack.size() - 1;
    TraceNode current = stack.get(lastIdx);
    TraceNode marked = new TraceNode(
        current.type(), current.expression(), current.operator(), current.value(),
        current.result(), true, current.elapsedNanos(), current.children(), current.error());
    stack.set(lastIdx, marked);
    if (stack.size() == 1) {
      rootNodes.set(rootNodes.size() - 1, marked);
    }
  }

  /**
   * 获取全部根节点的不可变快照。
   *
   * @return 追踪树根节点列表
   */
  public List<TraceNode> getRootNodes() {
    return new ArrayList<>(rootNodes);
  }

  /**
   * 清空全部追踪状态。
   */
  public void clear() {
    rootNodes.clear();
    stack.clear();
  }

  /**
   * 判断当前是否有活跃的追踪根节点。
   *
   * @return true 表示有
   */
  public boolean isActive() {
    return !stack.isEmpty();
  }
}
