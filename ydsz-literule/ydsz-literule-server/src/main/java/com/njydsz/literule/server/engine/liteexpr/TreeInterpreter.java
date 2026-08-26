package com.njydsz.literule.server.engine.liteexpr;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.literule.server.debug.RuleDebugger;

/**
 * LiteExpr AST 树形遍历解释器
 *
 * <p>递归遍历 {@link ExprNode} AST 执行表达式求值。核心特性：
 *
 * <ul>
 *   <li><b>短路求值</b>：AND 左侧 false 跳过右侧；OR 左侧 true 跳过右侧
 *   <li><b>自动类型转换</b>：int + BigDecimal → BigDecimal（不丢精度）
 *   <li><b>空值安全</b>：null.x 返回 null 而非 NPE
 *   <li><b>函数调用</b>：通过 {@link FunctionRegistry} 查找并执行
 *   <li><b>追踪树构建</b>：求值过程中同步构建 {@link ExprTraceBuilder} 追踪树
 *   <li><b>线程安全</b>：解释器为无状态单例，每次求值的变量上下文/追踪器/递归深度存放于
 *       {@link ThreadLocal} 会话中（P0-2 并发修复），并发求值互不干扰
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class TreeInterpreter implements ExprNodeVisitor<Object> {

  /** 最小递归深度限制 */
  private static final int MIN_RECURSION_DEPTH = 16;

  /**
   * 默认 AST 递归深度上限（P1-5：防御恶意嵌套表达式导致的 StackOverflow）
   *
   * <p>超过此深度时抛 {@link LiteExprException} 明确错误而非 JVM 崩溃。
   * 正常业务表达式嵌套深度通常在 5-20 层内，128 层足以覆盖合理场景。
   */
  public static final int DEFAULT_MAX_RECURSION_DEPTH = 128;

  /**
   * 默认单次求值节点访问预算（P0-6：防御超长平铺结构/循环式递归导致的 CPU 耗尽）
   *
   * <p>解释器为树遍历，每访问一个 AST 节点计数一次。典型业务表达式节点数 < 10k，
   * 100 万节点预算足以覆盖合理场景，同时阻止病态表达式无限执行。
   */
  public static final long DEFAULT_MAX_STEPS = 1_000_000L;

  private final FunctionRegistry functionRegistry;

  /** 单次求值会话（ThreadLocal，保证并发安全） */
  private final ThreadLocal<EvalSession> session = new ThreadLocal<>();

  /** 递归深度上限（可通过系统属性覆盖） */
  private final int maxRecursionDepth;

  /** 单次求值节点访问预算 */
  private final long maxSteps;

  /**
   * 单次求值会话：承载变量上下文、追踪树构建器、递归深度与执行预算，随求值调用创建与销毁。
   *
   * @param variables 变量上下文（facts）
   */
  private static final class EvalSession {
    final Map<String, Object> variables;
    ExprTraceBuilder traceBuilder;
    int currentDepth = 0;
    /** 已访问节点数 */
    long stepCount = 0;
    /** 求值截止时间（纳秒）；0=不启用墙上时钟超时 */
    long deadlineNanos = 0;

    EvalSession(Map<String, Object> variables) {
      this.variables = variables;
    }
  }

  public TreeInterpreter(FunctionRegistry functionRegistry) {
    this(functionRegistry, DEFAULT_MAX_RECURSION_DEPTH, DEFAULT_MAX_STEPS);
  }

  /**
   * 带自定义递归深度上限的解释器构造器
   *
   * @param functionRegistry 函数注册表
   * @param maxRecursionDepth 递归深度上限（最小 16）
   */
  public TreeInterpreter(FunctionRegistry functionRegistry, int maxRecursionDepth) {
    this(functionRegistry, maxRecursionDepth, DEFAULT_MAX_STEPS);
  }

  /**
   * 带自定义递归深度上限与执行预算的解释器构造器
   *
   * @param functionRegistry 函数注册表
   * @param maxRecursionDepth 递归深度上限（最小 16）
   * @param maxSteps 单次求值节点访问预算（最小 1024）
   */
  public TreeInterpreter(FunctionRegistry functionRegistry, int maxRecursionDepth, long maxSteps) {
    this.functionRegistry = functionRegistry;
    this.maxRecursionDepth = Math.max(MIN_RECURSION_DEPTH, maxRecursionDepth);
    this.maxSteps = Math.max(1024L, maxSteps);
  }

  /**
   * 开启一次求值会话
   *
   * @param facts 变量上下文
   * @return 会话实例
   */
  private EvalSession beginSession(Map<String, Object> facts) {
    EvalSession evalSession = new EvalSession(facts);
    session.set(evalSession);
    return evalSession;
  }

  /**
   * 获取当前线程的求值会话
   *
   * @return 会话实例
   */
  private EvalSession requireSession() {
    EvalSession evalSession = session.get();
    if (evalSession == null) {
      throw new IllegalStateException("LiteExpr 求值会话未初始化，请在 eval/evalWithTrace 调用链内使用");
    }
    return evalSession;
  }

  /**
   * 检查递归深度与执行预算是否超限（P0-6）
   *
   * <p>每访问一个节点计数一次：超过 {@link #maxSteps} 或超过墙上时钟截止时间时，
   * 抛 {@link LiteExprException} 明确错误而非无限执行。墙上时钟检查每 256 步采样一次，
   * 避免高频 {@code System.nanoTime()} 开销。
   *
   * @param node 当前节点（用于错误行列号定位）
   * @throws LiteExprException 超过递归深度 / 节点预算 / 求值超时上限时
   */
  private void guard(ExprNode node) {
    EvalSession evalSession = requireSession();
    long steps = ++evalSession.stepCount;
    if (steps > maxSteps) {
      throw new LiteExprException(
          String.format(
              "表达式节点访问超过上限 %d，请检查是否存在超长平铺结构或循环式递归", maxSteps),
          node != null ? node.line() : 0,
          node != null ? node.column() : 0);
    }
    if (evalSession.deadlineNanos > 0 && (steps & 0xFF) == 0) {
      if (System.nanoTime() > evalSession.deadlineNanos) {
        throw new LiteExprException(
            "表达式求值超时（超过配置的求值时限），请简化表达式或拆分规则",
            node != null ? node.line() : 0,
            node != null ? node.column() : 0);
      }
    }
    if (evalSession.currentDepth >= maxRecursionDepth) {
      throw new LiteExprException(
          String.format("表达式递归深度超过上限 %d，请检查表达式是否存在过深嵌套或循环引用", maxRecursionDepth),
          node != null ? node.line() : 0,
          node != null ? node.column() : 0);
    }
  }

  /**
   * 求值（不带追踪）
   *
   * @param ast AST 根节点
   * @param facts 变量上下文
   * @return 求值结果
   */
  public Object eval(ExprNode ast, Map<String, Object> facts) {
    return eval(ast, facts, 0L);
  }

  /**
   * 求值（不带追踪，支持求值超时，P0-6）
   *
   * @param ast AST 根节点
   * @param facts 变量上下文
   * @param timeoutNanos 求值超时（纳秒）；0 或负数 = 不启用墙上时钟超时（节点预算仍生效）
   * @return 求值结果
   */
  public Object eval(ExprNode ast, Map<String, Object> facts, long timeoutNanos) {
    EvalSession evalSession = beginSession(facts);
    try {
      evalSession.traceBuilder = null;
      evalSession.deadlineNanos = timeoutNanos > 0 ? System.nanoTime() + timeoutNanos : 0L;
      return ast.accept(this);
    } finally {
      session.remove();
    }
  }

  /**
   * 求值（带追踪树）
   *
   * @param ast AST 根节点
   * @param facts 变量上下文
   * @return 追踪结果（含最终值和追踪树）
   */
  public TraceEvalResult evalWithTrace(ExprNode ast, Map<String, Object> facts) {
    return evalWithTrace(ast, facts, 0L);
  }

  /**
   * 求值（带追踪树，支持求值超时，P0-6）
   *
   * @param ast AST 根节点
   * @param facts 变量上下文
   * @param timeoutNanos 求值超时（纳秒）；0 或负数 = 不启用墙上时钟超时（节点预算仍生效）
   * @return 追踪结果（含最终值和追踪树）
   */
  public TraceEvalResult evalWithTrace(ExprNode ast, Map<String, Object> facts, long timeoutNanos) {
    EvalSession evalSession = beginSession(facts);
    try {
      evalSession.traceBuilder = new ExprTraceBuilder();
      evalSession.deadlineNanos = timeoutNanos > 0 ? System.nanoTime() + timeoutNanos : 0L;
      Object result = ast.accept(this);
      ExprTraceBuilder.TraceNode traceTree = evalSession.traceBuilder.buildRoot(ast, result);
      return new TraceEvalResult(result, traceTree);
    } finally {
      session.remove();
    }
  }

  // ===== Visitor 方法 =====

  @Override
  public Object visitLiteral(LiteralNode node) {
    guard(node);
    return node.value();
  }

  @Override
  public Object visitVariable(VariableNode node) {
    guard(node);
    debugCheckNode(node, "VARIABLE");
    EvalSession evalSession = requireSession();
    Object value = evalSession.variables.get(node.name());
    if (evalSession.traceBuilder != null) {
      evalSession.traceBuilder.recordVariable(node.name(), value);
    }
    return value;
  }

  @Override
  public Object visitBinaryOp(BinaryOpNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      String op = node.operator();
      // F1 断点调试：比较/逻辑/算术节点求值前挂起（仅调试会话激活时生效）
      debugCheckNode(node, node.isLogical() ? "LOGICAL" : node.isComparison() ? "COMPARISON" : "ARITHMETIC");

      // 短路求值
      if ("&&".equals(op) || "and".equals(op)) {
        Object leftVal = node.left().accept(this);
        boolean leftBool = BuiltinFunctions.toBool(leftVal);
        if (!leftBool) {
          if (evalSession.traceBuilder != null) {
            evalSession.traceBuilder.recordLogical(op, false, true, node);
          }
          return false;
        }
        Object rightVal = node.right().accept(this);
        boolean rightBool = BuiltinFunctions.toBool(rightVal);
        if (evalSession.traceBuilder != null) {
          evalSession.traceBuilder.recordLogical(op, rightBool, false, node);
        }
        return rightBool;
      }

      if ("||".equals(op) || "or".equals(op)) {
        Object leftVal = node.left().accept(this);
        boolean leftBool = BuiltinFunctions.toBool(leftVal);
        if (leftBool) {
          if (evalSession.traceBuilder != null) {
            evalSession.traceBuilder.recordLogical(op, true, true, node);
          }
          return true;
        }
        Object rightVal = node.right().accept(this);
        boolean rightBool = BuiltinFunctions.toBool(rightVal);
        if (evalSession.traceBuilder != null) {
          evalSession.traceBuilder.recordLogical(op, rightBool, false, node);
        }
        return rightBool;
      }

      // 非短路运算
      Object leftVal = node.left().accept(this);
      Object rightVal = node.right().accept(this);
      Object result = applyBinaryOp(op, leftVal, rightVal);

      if (evalSession.traceBuilder != null) {
        evalSession.traceBuilder.recordBinary(op, leftVal, rightVal, result, node);
      }
      return result;
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitUnaryOp(UnaryOpNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      Object operandVal = node.operand().accept(this);
      String op = node.operator();
      Object result;
      if ("!".equals(op) || "not".equals(op)) {
        result = !BuiltinFunctions.toBool(operandVal);
      } else if ("-".equals(op)) {
        result =
            BuiltinFunctions.isIntegerLike(operandVal)
                ? -BuiltinFunctions.toLong(operandVal)
                : BuiltinFunctions.toDecimal(operandVal).negate();
      } else {
        throw new LiteExprException("未知一元运算符: " + op, node.line(), node.column());
      }
      if (evalSession.traceBuilder != null) {
        evalSession.traceBuilder.recordUnary(op, operandVal, result, node);
      }
      return result;
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitTernary(TernaryNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      Object condVal = node.condition().accept(this);
      boolean cond = BuiltinFunctions.toBool(condVal);
      Object result = cond ? node.thenExpr().accept(this) : node.elseExpr().accept(this);
      if (evalSession.traceBuilder != null) {
        evalSession.traceBuilder.recordTernary(cond, result, node);
      }
      return result;
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitFunctionCall(FunctionCallNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      debugCheckNode(node, "FUNCTION_CALL");
      String funcName = node.functionName();
      LiteExprFunction function = functionRegistry.lookup(funcName);
      if (function == null) {
        throw new LiteExprException("未定义的函数: " + funcName, node.line(), node.column());
      }

      // 求值参数
      Object[] argValues = new Object[node.arguments().size()];
      for (int i = 0; i < node.arguments().size(); i++) {
        argValues[i] = node.arguments().get(i).accept(this);
      }

      try {
        Object result = function.call(argValues);
        if (evalSession.traceBuilder != null) {
          evalSession.traceBuilder.recordFunctionCall(funcName, argValues, result, node);
        }
        return result;
      } catch (LiteExprException e) {
        throw e;
      } catch (Exception e) {
        throw new LiteExprException(
            "函数 '" + funcName + "' 执行失败: " + e.getMessage(), node.line(), node.column(), e);
      }
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitMemberAccess(MemberAccessNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      Object target = node.target().accept(this);
      if (target == null) {
        return null;
      }

      String member = node.member();
      Object result;

      if (target instanceof Map<?, ?> map) {
        result = map.get(member);
      } else if (target instanceof List<?> list) {
        // List 上没有属性，但可能有一些伪属性
        result =
            switch (member) {
              case "size" -> list.size();
              case "isEmpty" -> list.isEmpty();
              default -> getFieldValue(target, member);
            };
      } else {
        result = getFieldValue(target, member);
      }

      if (evalSession.traceBuilder != null) {
        evalSession.traceBuilder.recordMemberAccess(target, member, result, node);
      }
      return result;
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitIndex(IndexNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      Object target = node.target().accept(this);
      if (target == null) {
        return null;
      }

      Object index = node.index().accept(this);
      Object result;

      if (target instanceof List<?> list) {
        int idx = BuiltinFunctions.toInt(index);
        result = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
      } else if (target instanceof Map<?, ?> map) {
        result = map.get(index);
      } else if (target instanceof String str) {
        int idx = BuiltinFunctions.toInt(index);
        result = (idx >= 0 && idx < str.length()) ? String.valueOf(str.charAt(idx)) : null;
      } else if (target.getClass().isArray()) {
        int idx = BuiltinFunctions.toInt(index);
        result = (idx >= 0 && idx < Array.getLength(target)) ? Array.get(target, idx) : null;
      } else {
        result = null;
      }

      return result;
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitList(ListNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      List<Object> result = new ArrayList<>(node.elements().size());
      for (ExprNode element : node.elements()) {
        result.add(element.accept(this));
      }
      return result;
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitMap(MapNode node) {
    guard(node);
    EvalSession evalSession = requireSession();
    evalSession.currentDepth++;
    try {
      Map<Object, Object> result = new LinkedHashMap<>(node.entries().size());
      for (Map.Entry<ExprNode, ExprNode> entry : node.entries().entrySet()) {
        Object key = entry.getKey().accept(this);
        Object value = entry.getValue().accept(this);
        result.put(key, value);
      }
      return result;
    } finally {
      evalSession.currentDepth--;
    }
  }

  @Override
  public Object visitLambda(LambdaNode node) {
    // Lambda 转为 LiteExprFunction（闭包绑定当前会话的变量上下文）
    EvalSession evalSession = requireSession();
    return (LiteExprFunction)
        args -> {
          Map<String, Object> captured = evalSession.variables;
          Object oldValue = captured.put(node.parameter(), args[0]);
          try {
            return node.body().accept(this);
          } finally {
            if (oldValue != null) {
              captured.put(node.parameter(), oldValue);
            } else {
              captured.remove(node.parameter());
            }
          }
        };
  }

  @Override
  public Object visitTemplateString(TemplateStringNode node) {
    guard(node);
    StringBuilder sb = new StringBuilder();
    for (ExprNode part : node.parts()) {
      if (part instanceof LiteralNode ln) {
        sb.append(ln.value() == null ? "" : ln.value());
      } else {
        Object val = part.accept(this);
        sb.append(val == null ? "" : val);
      }
    }
    return sb.toString();
  }

  // ===== 二元运算实现 =====

  private Object applyBinaryOp(String op, Object left, Object right) {
    if (left == null || right == null) {
      return applyNullBinaryOp(op, left, right);
    }

    return switch (op) {
      case "+" -> {
        if (left instanceof String || right instanceof String) {
          yield BuiltinFunctions.str(left) + BuiltinFunctions.str(right);
        }
        yield BuiltinFunctions.smartAdd(left, right);
      }
      case "-" -> BuiltinFunctions.smartSubtract(left, right);
      case "*" -> BuiltinFunctions.smartMultiply(left, right);
      case "/" -> {
        BigDecimal divisor = BuiltinFunctions.toDecimal(right);
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
          yield null;
        }
        yield BuiltinFunctions.toDecimal(left).divide(divisor, 10, RoundingMode.HALF_UP);
      }
      case "%" -> BuiltinFunctions.smartRemainder(left, right);
      case "==" -> equals(left, right);
      case "!=" -> !equals(left, right);
      case ">" -> BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) > 0;
      case ">=" ->
          BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) >= 0;
      case "<" -> BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) < 0;
      case "<=" ->
          BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) <= 0;
      default -> throw new LiteExprException("未知运算符: " + op, 0, 0);
    };
  }

  private Object applyNullBinaryOp(String op, Object left, Object right) {
    return switch (op) {
      case "==" -> left == right;
      case "!=" -> left != right;
      case "+" -> {
        if (left == null && right == null) {
          yield null;
        }
        yield BuiltinFunctions.str(left) + BuiltinFunctions.str(right);
      }
      default -> null;
    };
  }

  private boolean equals(Object a, Object b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    // 数值比较
    if (a instanceof Number && b instanceof Number) {
      return BuiltinFunctions.toDecimal(a).compareTo(BuiltinFunctions.toDecimal(b)) == 0;
    }
    if (a.getClass() != b.getClass()) {
      // 尝试字符串比较
      return a.toString().equals(b.toString());
    }
    return a.equals(b);
  }

  /** 通过反射获取对象字段值（用于 POJO 属性访问） */
  private Object getFieldValue(Object target, String fieldName) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return field.get(target);
    } catch (NoSuchFieldException e) {
      // 尝试 getter 方法
      try {
        String getterName =
            "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        Method getter = target.getClass().getMethod(getterName);
        return getter.invoke(target);
      } catch (Exception e2) {
        return null;
      }
    } catch (Exception e) {
      return null;
    }
  }

  // ===== 追踪结果 =====

  /**
   * 带追踪的求值结果。
   *
   * @param value 求值结果值
   * @param traceTree 追踪树根节点
   */
  public record TraceEvalResult(Object value, ExprTraceBuilder.TraceNode traceTree) {}

  // ===== 断点调试集成（F1，零侵入：未配置调试器时为 no-op） =====

  /**
   * 表达式节点级断点检查（F1 断点调试器）
   *
   * <p>在每个关键节点求值前调用，命中时由 {@link DebugSession} 挂起当前求值线程。 未配置调试器或非调试评估时立即返回，无任何开销。
   *
   * @param node AST 节点
   * @param nodeType 节点类型（COMPARISON/LOGICAL/ARITHMETIC/VARIABLE/FUNCTION_CALL）
   */
  private void debugCheckNode(ExprNode node, String nodeType) {
    RuleDebugger debugger = RuleDebugger.get();
    if (debugger == null) {
      return;
    }
    String ruleCode = RuleDebugger.currentRuleCode();
    if (ruleCode == null) {
      return;
    }
    try {
      debugger.checkExpressionBreakpoint(ruleCode, nodeType, node.exprText(), requireSession().variables);
    } catch (Exception e) {
      // 断点挂起异常不应中断求值（调试器故障隔离）
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
