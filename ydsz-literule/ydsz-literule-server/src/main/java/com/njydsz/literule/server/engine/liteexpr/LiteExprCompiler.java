package com.njydsz.literule.server.engine.liteexpr;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * LiteExpr 编译器
 *
 * <p>负责将表达式文本编译为 AST，并提供：
 *
 * <ul>
 *   <li><b>编译缓存</b>：{@code String → ExprNode} 缓存，避免重复解析。 基于 ydsz-common-cache 实现，上限 4096 条、1
 *       小时访问过期（P2-3）， 避免无界增长导致 OOM
 *   <li><b>常量折叠</b>：编译期求值常量表达式（如 {@code 1 + 2} → {@code 3}）
 *   <li><b>变量提取</b>：从 AST 中收集所有变量引用
 *   <li><b>函数提取</b>：从 AST 中收集所有函数调用
 *   <li><b>AST 级错误定位</b>：编译错误携带精确行列号
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class LiteExprCompiler {

  /** 缓存上限 */
  private static final int MAX_CACHE_SIZE = 4096;

  /** 缓存访问后过期时间（小时） */
  private static final long CACHE_EXPIRE_HOURS = 1L;

  /**
   * 编译缓存：表达式文本 → AST
   *
   * <p>P2-3 改用 ydsz-common-cache 替代 {@code ConcurrentHashMap}：
   *
   * <ul>
   *   <li>{@code maximumSize(4096)} — 原硬编码常量从未被强制执行，导致无界增长 OOM 风险
   *   <li>{@code expireAfterAccess(1h)} — 长期未访问的表达式自动淘汰，避免规则下线后仍驻留
   *   <li>线程安全，{@code get(key, mapper)} 原子加载，避免 {@code computeIfAbsent} 的重入陷阱
   * </ul>
   */
  private final Cache<String, ExprNode> cache =
      YdszCache.<String, ExprNode>newBuilder()
          .type(CacheType.STRIPED)
          .maximumSize(MAX_CACHE_SIZE)
          .expireAfterAccess(CACHE_EXPIRE_HOURS, TimeUnit.HOURS)
          .build();

  /**
   * 编译表达式（带缓存）
   *
   * @param expression 表达式文本
   * @return AST 根节点
   * @throws LiteExprException 编译失败
   */
  public ExprNode compile(String expression) {
    if (expression == null || expression.isBlank()) {
      throw new LiteExprException("表达式为空", 1, 1);
    }
    return cache.get(expression, this::compile0);
  }

  /** 实际编译逻辑（无缓存） */
  private ExprNode compile0(String expression) {
    // 1. 词法分析
    ExprLexer lexer = new ExprLexer(expression);
    List<Token> tokens = lexer.tokenize();

    // 2. 语法分析
    ExprParser parser = new ExprParser(tokens);
    ExprNode ast = parser.parse();

    // 3. 常量折叠优化
    return constantFold(ast);
  }

  /** 清空编译缓存 */
  public void clearCache() {
    cache.invalidateAll();
  }

  /** 当前缓存数量（估计值，本地缓存异步维护，非精确） */
  public long cacheSize() {
    return cache.estimatedSize();
  }

  // ===== 常量折叠 =====

  /**
   * 常量折叠：递归地将编译期可求值的子表达式替换为字面值
   *
   * <p>示例：
   *
   * <ul>
   *   <li>{@code 1 + 2} → {@code 3}
   *   <li>{@code true && false} → {@code false}
   *   <li>{@code "a" + "b"} → {@code "ab"}
   *   <li>{@code !true} → {@code false}
   *   <li>{@code true ? 1 : 2} → {@code 1}
   * </ul>
   *
   * <p>仅折叠全字面量子表达式，包含变量的子表达式不折叠。
   */
  public ExprNode constantFold(ExprNode node) {
    if (node == null) return null;

    return switch (node) {
      case LiteralNode ln -> ln;
      case VariableNode vn -> vn;
      case BinaryOpNode bon -> {
        ExprNode left = constantFold(bon.left());
        ExprNode right = constantFold(bon.right());
        if (left instanceof LiteralNode ll && right instanceof LiteralNode rl) {
          Object result = tryEvalBinary(bon.operator(), ll.value(), rl.value());
          if (result != null) {
            yield new LiteralNode(result, bon.line(), bon.column());
          }
        }
        yield new BinaryOpNode(bon.operator(), left, right, bon.line(), bon.column());
      }
      case UnaryOpNode uon -> {
        ExprNode operand = constantFold(uon.operand());
        if (operand instanceof LiteralNode ol) {
          Object result = tryEvalUnary(uon.operator(), ol.value());
          if (result != null) {
            yield new LiteralNode(result, uon.line(), uon.column());
          }
        }
        yield new UnaryOpNode(uon.operator(), operand, uon.line(), uon.column());
      }
      case TernaryNode tn -> {
        ExprNode cond = constantFold(tn.condition());
        ExprNode thenE = constantFold(tn.thenExpr());
        ExprNode elseE = constantFold(tn.elseExpr());
        if (cond instanceof LiteralNode cl && cl.value() instanceof Boolean b) {
          yield b ? thenE : elseE;
        }
        yield new TernaryNode(cond, thenE, elseE, tn.line(), tn.column());
      }
      case FunctionCallNode fcn -> {
        List<ExprNode> foldedArgs = new ArrayList<>(fcn.arguments().size());
        for (ExprNode arg : fcn.arguments()) {
          foldedArgs.add(constantFold(arg));
        }
        yield new FunctionCallNode(fcn.functionName(), foldedArgs, fcn.line(), fcn.column());
      }
      case MemberAccessNode man -> {
        ExprNode target = constantFold(man.target());
        yield new MemberAccessNode(target, man.member(), man.line(), man.column());
      }
      case IndexNode in -> {
        ExprNode target = constantFold(in.target());
        ExprNode index = constantFold(in.index());
        yield new IndexNode(target, index, in.line(), in.column());
      }
      case ListNode ln -> {
        List<ExprNode> folded = new ArrayList<>(ln.elements().size());
        for (ExprNode e : ln.elements()) folded.add(constantFold(e));
        yield new ListNode(folded, ln.line(), ln.column());
      }
      case MapNode mn -> {
        Map<ExprNode, ExprNode> folded = new LinkedHashMap<>(mn.entries().size());
        for (Map.Entry<ExprNode, ExprNode> e : mn.entries().entrySet()) {
          folded.put(constantFold(e.getKey()), constantFold(e.getValue()));
        }
        yield new MapNode(folded, mn.line(), mn.column());
      }
      case LambdaNode lan -> {
        ExprNode body = constantFold(lan.body());
        yield new LambdaNode(lan.parameter(), body, lan.line(), lan.column());
      }
      case TemplateStringNode tsn -> {
        List<ExprNode> folded = new ArrayList<>(tsn.parts().size());
        for (ExprNode p : tsn.parts()) folded.add(constantFold(p));
        yield new TemplateStringNode(folded, tsn.line(), tsn.column());
      }
      case null -> null;
    };
  }

  /**
   * 尝试在编译期求值二元运算（常量折叠辅助）
   *
   * @return 求值结果；无法求值返回 null
   */
  private Object tryEvalBinary(String op, Object left, Object right) {
    try {
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
          var divisor = BuiltinFunctions.toDecimal(right);
          if (divisor.signum() == 0) yield null;
          yield BuiltinFunctions.toDecimal(left).divide(divisor, 10, RoundingMode.HALF_UP);
        }
        case "%" -> BuiltinFunctions.smartRemainder(left, right);
        case "==" -> left != null && left.equals(right);
        case "!=" -> left == null || !left.equals(right);
        case ">" ->
            BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) > 0;
        case ">=" ->
            BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) >= 0;
        case "<" ->
            BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) < 0;
        case "<=" ->
            BuiltinFunctions.toDecimal(left).compareTo(BuiltinFunctions.toDecimal(right)) <= 0;
        case "&&", "and" -> BuiltinFunctions.toBool(left) && BuiltinFunctions.toBool(right);
        case "||", "or" -> BuiltinFunctions.toBool(left) || BuiltinFunctions.toBool(right);
        default -> null;
      };
    } catch (Exception e) {
      return null;
    }
  }

  /** 尝试在编译期求值一元运算 */
  private Object tryEvalUnary(String op, Object operand) {
    try {
      return switch (op) {
        case "!", "not" -> !BuiltinFunctions.toBool(operand);
        case "-", "neg" ->
            BuiltinFunctions.isIntegerLike(operand)
                ? -BuiltinFunctions.toLong(operand)
                : BuiltinFunctions.toDecimal(operand).negate();
        default -> null;
      };
    } catch (Exception e) {
      return null;
    }
  }

  // ===== 变量提取 =====

  /**
   * 从 AST 中提取所有变量引用名
   *
   * <p>遍历 AST 收集 {@link VariableNode}，过滤内置关键字。 不依赖正则，比 Aviator/QLExpress 实现更准确。
   *
   * @param ast AST 根节点
   * @return 变量名列表（去重，保留出现顺序）
   */
  public List<String> extractVariables(ExprNode ast) {
    Set<String> variables = new LinkedHashSet<>();
    collectVariables(ast, variables);
    return new ArrayList<>(variables);
  }

  private void collectVariables(ExprNode node, Set<String> variables) {
    if (node == null) return;
    switch (node) {
      case VariableNode vn -> variables.add(vn.name());
      case BinaryOpNode bon -> {
        collectVariables(bon.left(), variables);
        collectVariables(bon.right(), variables);
      }
      case UnaryOpNode uon -> collectVariables(uon.operand(), variables);
      case TernaryNode tn -> {
        collectVariables(tn.condition(), variables);
        collectVariables(tn.thenExpr(), variables);
        collectVariables(tn.elseExpr(), variables);
      }
      case FunctionCallNode fcn -> fcn.arguments().forEach(a -> collectVariables(a, variables));
      case MemberAccessNode man -> collectVariables(man.target(), variables);
      case IndexNode in -> {
        collectVariables(in.target(), variables);
        collectVariables(in.index(), variables);
      }
      case ListNode ln -> ln.elements().forEach(e -> collectVariables(e, variables));
      case MapNode mn ->
          mn.entries()
              .forEach(
                  (k, v) -> {
                    collectVariables(k, variables);
                    collectVariables(v, variables);
                  });
      case LambdaNode lan -> {
        // Lambda 参数不是外部变量引用
        Set<String> inner = new LinkedHashSet<>();
        collectVariables(lan.body(), inner);
        inner.remove(lan.parameter());
        variables.addAll(inner);
      }
      case TemplateStringNode tsn -> tsn.parts().forEach(p -> collectVariables(p, variables));
      default -> {}
    }
  }

  /** 从 AST 中提取所有函数调用名 */
  public List<String> extractFunctions(ExprNode ast) {
    Set<String> functions = new LinkedHashSet<>();
    collectFunctions(ast, functions);
    return new ArrayList<>(functions);
  }

  private void collectFunctions(ExprNode node, Set<String> functions) {
    if (node == null) return;
    switch (node) {
      case FunctionCallNode fcn -> {
        functions.add(fcn.functionName());
        fcn.arguments().forEach(a -> collectFunctions(a, functions));
      }
      case BinaryOpNode bon -> {
        collectFunctions(bon.left(), functions);
        collectFunctions(bon.right(), functions);
      }
      case UnaryOpNode uon -> collectFunctions(uon.operand(), functions);
      case TernaryNode tn -> {
        collectFunctions(tn.condition(), functions);
        collectFunctions(tn.thenExpr(), functions);
        collectFunctions(tn.elseExpr(), functions);
      }
      case MemberAccessNode man -> collectFunctions(man.target(), functions);
      case IndexNode in -> {
        collectFunctions(in.target(), functions);
        collectFunctions(in.index(), functions);
      }
      case ListNode ln -> ln.elements().forEach(e -> collectFunctions(e, functions));
      case MapNode mn ->
          mn.entries()
              .forEach(
                  (k, v) -> {
                    collectFunctions(k, functions);
                    collectFunctions(v, functions);
                  });
      case LambdaNode lan -> collectFunctions(lan.body(), functions);
      case TemplateStringNode tsn -> tsn.parts().forEach(p -> collectFunctions(p, functions));
      default -> {}
    }
  }
}
