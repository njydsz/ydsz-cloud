package com.njydsz.literule.server.engine.liteexpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.expression.ExpressionEngine;
import com.njydsz.literule.api.expression.ExpressionFunctionDef;
import com.njydsz.literule.api.expression.ExpressionTraceNode;
import com.njydsz.literule.api.expression.ExpressionValidationResult;

/**
 * LiteExpr 自研表达式求值器
 *
 * <p>实现 {@link ExpressionEngine} 接口，对接上层规则引擎。 完全自研实现，核心组件：
 *
 * <ul>
 *   <li>{@link LiteExprCompiler} — 编译缓存 + 常量折叠 + 变量提取
 *   <li>{@link TreeInterpreter} — AST 遍历执行 + 短路求值 + 追踪树
 *   <li>{@link FunctionRegistry} — 内置函数 + 业务函数注册
 *   <li>{@link LiteExprSandbox} — AST 级安全沙箱
 * </ul>
 *
 * <p>配置方式：
 *
 * <pre>
 * ydsz:
 *   literule:
 *     evaluator: liteexpr
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class LiteExprEngine implements ExpressionEngine {

    /** 表达式函数缓存容量（日志展示） */
  private static final int CACHE_CAPACITY = 512;

  /** 纳秒到毫秒的换算系数 */
  private static final long NANOS_PER_MILLI = 1_000_000L;

  private final LiteExprCompiler compiler;
  private final FunctionRegistry functionRegistry;
  private final TreeInterpreter interpreter;
  private final LiteExprSandbox sandbox;
  private final boolean sandboxEnabled;

  public LiteExprEngine() {
    this(true);
  }

  public LiteExprEngine(boolean sandboxEnabled) {
    this.sandboxEnabled = sandboxEnabled;
    this.compiler = new LiteExprCompiler();
    this.functionRegistry = new FunctionRegistry();
    this.interpreter = new TreeInterpreter(functionRegistry);
    this.sandbox = new LiteExprSandbox();
    this.sandbox.syncFunctions(functionRegistry);
    log.info(
        "[LiteExpr] 自研表达式引擎已初始化（sandbox={}, functions={}, cacheCapacity={})",
        sandboxEnabled,
        functionRegistry.getFunctionNames().size(),
        CACHE_CAPACITY);
  }

  @Override
  public boolean evalBoolean(String expression, RuleContext context) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    try {
      ExprNode ast = compileAndCheck(expression);
      if (sandboxEnabled && context != null) {
        sandbox.syncFacts(context.getFacts());
      }
      Map<String, Object> facts = context != null ? context.getFacts() : Map.of();
      Object result = interpreter.eval(ast, facts);
      if (result instanceof Boolean b) {
        return b;
      }
      if (result instanceof Number n) {
        return n.doubleValue() != 0;
      }
      if (result == null) {
        return false;
      }
      return Boolean.parseBoolean(String.valueOf(result));
    } catch (SecurityException e) {
      log.warn("[LiteExpr] 安全拦截: {}", e.getMessage());
      return false;
    } catch (Exception e) {
      log.warn("[LiteExpr] 布尔表达式求值失败: expr='{}', error={}", expression, e.getMessage());
      return false;
    }
  }

  @Override
  public Object eval(String expression, RuleContext context) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    try {
      ExprNode ast = compileAndCheck(expression);
      if (sandboxEnabled && context != null) {
        sandbox.syncFacts(context.getFacts());
      }
      Map<String, Object> facts = context != null ? context.getFacts() : Map.of();
      return interpreter.eval(ast, facts);
    } catch (SecurityException e) {
      log.warn("[LiteExpr] 安全拦截: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      log.warn("[LiteExpr] 表达式求值失败: expr='{}', error={}", expression, e.getMessage());
      return null;
    }
  }

  @Override
  public boolean validate(String expression) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    try {
      compileAndCheck(expression);
      return true;
    } catch (SecurityException e) {
      log.debug("[LiteExpr] 表达式被沙箱拦截: expr='{}', error={}", expression, e.getMessage());
      return false;
    } catch (Exception e) {
      log.debug("[LiteExpr] 表达式校验失败: expr='{}', error={}", expression, e.getMessage());
      return false;
    }
  }

  @Override
  public ExpressionValidationResult validateDetailed(String expression) {
    long start = System.nanoTime();

    // 1. 空表达式
    if (expression == null || expression.isBlank()) {
      long elapsed = (System.nanoTime() - start) / NANOS_PER_MILLI;
      return ExpressionValidationResult.fail(
          expression, ExpressionValidationResult.ErrorType.EMPTY, "表达式为空", elapsed);
    }

    // 2. 编译校验（Lexer + Parser）
    ExprNode ast;
    try {
      ast = compiler.compile(expression);
    } catch (LiteExprException e) {
      long elapsed = (System.nanoTime() - start) / NANOS_PER_MILLI;
      return ExpressionValidationResult.builder()
          .valid(false)
          .errorType(ExpressionValidationResult.ErrorType.SYNTAX_ERROR)
          .errorMessage(e.getMessage())
          .errorLine(e.getLine())
          .errorColumn(e.getColumn())
          .expression(expression)
          .parseTimeMs(elapsed)
          .referencedVariables(new ArrayList<>())
          .build();
    } catch (Exception e) {
      long elapsed = (System.nanoTime() - start) / NANOS_PER_MILLI;
      return ExpressionValidationResult.fail(
          expression, ExpressionValidationResult.ErrorType.SYNTAX_ERROR, e.getMessage(), elapsed);
    }

    // 3. 沙箱校验
    if (sandboxEnabled) {
      LiteExprSandbox.SandboxResult sandboxResult = sandbox.check(ast);
      if (!sandboxResult.passed()) {
        long elapsed = (System.nanoTime() - start) / NANOS_PER_MILLI;
        return ExpressionValidationResult.fail(
            expression,
            ExpressionValidationResult.ErrorType.SANDBOX_VIOLATION,
            sandboxResult.violationSummary(),
            elapsed);
      }
    }

    // 4. 编译通过，提取引用变量
    long elapsed = (System.nanoTime() - start) / NANOS_PER_MILLI;
    List<String> vars = compiler.extractVariables(ast);
    return ExpressionValidationResult.ok(expression, elapsed, vars);
  }

  @Override
  public TraceResult evalBooleanWithTrace(String expression, RuleContext context) {
    if (expression == null || expression.isBlank()) {
      ExpressionTraceNode root =
          ExpressionTraceNode.builder()
              .nodeType(ExpressionTraceNode.NodeType.ROOT)
              .expression(expression)
              .result(false)
              .error("表达式为空")
              .build();
      return new TraceResult(false, root);
    }
    long start = System.nanoTime();
    try {
      ExprNode ast = compileAndCheck(expression);
      if (sandboxEnabled && context != null) {
        sandbox.syncFacts(context.getFacts());
      }
      Map<String, Object> facts = context != null ? context.getFacts() : Map.of();
      TreeInterpreter.TraceEvalResult traceResult = interpreter.evalWithTrace(ast, facts);
      long elapsed = System.nanoTime() - start;

      boolean boolResult;
      Object val = traceResult.value();
      if (val instanceof Boolean b) {
        boolResult = b;
      } else if (val instanceof Number n) {
        boolResult = n.doubleValue() != 0;
      } else {
        boolResult = Boolean.parseBoolean(String.valueOf(val));
      }

      ExpressionTraceNode root =
          convertTraceTree(traceResult.traceTree(), expression, boolResult, elapsed);
      return new TraceResult(boolResult, root);
    } catch (SecurityException e) {
      long elapsed = System.nanoTime() - start;
      ExpressionTraceNode root =
          ExpressionTraceNode.builder()
              .nodeType(ExpressionTraceNode.NodeType.ROOT)
              .expression(expression)
              .result(false)
              .error("沙箱拦截: " + e.getMessage())
              .elapsedNanos(elapsed)
              .build();
      return new TraceResult(false, root);
    } catch (Exception e) {
      long elapsed = System.nanoTime() - start;
      ExpressionTraceNode root =
          ExpressionTraceNode.builder()
              .nodeType(ExpressionTraceNode.NodeType.ROOT)
              .expression(expression)
              .result(false)
              .error("求值异常: " + e.getMessage())
              .elapsedNanos(elapsed)
              .build();
      return new TraceResult(false, root);
    }
  }

  @Override
  public List<ExpressionFunctionDef> registeredFunctionDefs() {
    List<ExpressionFunctionDef> defs = new ArrayList<>();
    for (String name : functionRegistry.listFunctionNames()) {
      String sig = functionRegistry.getSignature(name);
      String desc = functionRegistry.getDescription(name);
      if (sig != null) {
        defs.add(
            new ExpressionFunctionDef(
                name, sig, desc != null ? desc : "", null, inferCategory(name), "liteexpr"));
      }
    }
    return defs;
  }

  /** 获取函数注册表（用于业务侧注册自定义函数）
   * @return 返回值说明
   */
  public FunctionRegistry getFunctionRegistry() {
    return functionRegistry;
  }

  /** 获取编译器（用于缓存管理等）
   * @return 返回值说明
   */
  public LiteExprCompiler getCompiler() {
    return compiler;
  }

  /** 清空编译缓存 + 沙箱校验缓存（P1-3） */
  public void clearCache() {
    compiler.clearCache();
    sandbox.clearCache();
  }

  /**
   * 应用沙箱扩展策略（O2 沙箱规则外置化）
   *
   * <p>将 YAML 配置中的危险方法/类根追加到沙箱黑名单，白名单函数合并到函数白名单。
   * 业务方无需改代码即可收紧/放宽沙箱规则。
   *
   * @param forbiddenMethods 追加的危险方法名（可为 null）
   * @param forbiddenRoots 追加的危险类/属性链根（可为 null）
   * @param allowedFunctions 追加的白名单函数（可为 null）
   */
  public void applySandboxPolicy(
      Iterable<String> forbiddenMethods,
      Iterable<String> forbiddenRoots,
      Iterable<String> allowedFunctions) {
    sandbox.applyPolicy(forbiddenMethods, forbiddenRoots, allowedFunctions);
  }

  /** 缓存大小
   * @return 返回值说明
   */
  public long cacheSize() {
    return compiler.cacheSize();
  }

  // ===== 内部方法 =====

  /** 编译 + 沙箱校验 */
  private ExprNode compileAndCheck(String expression) {
    ExprNode ast = compiler.compile(expression);
    if (sandboxEnabled) {
      LiteExprSandbox.SandboxResult result = sandbox.check(ast);
      if (!result.passed()) {
        throw new SecurityException("表达式被沙箱拦截: " + result.violationSummary());
      }
    }
    return ast;
  }

  /** 将内部追踪树转换为 ExpressionTraceNode */
  private ExpressionTraceNode convertTraceTree(
      ExprTraceBuilder.TraceNode trace, String expression, Object result, long elapsedNanos) {
    ExpressionTraceNode.NodeType nodeType =
        switch (trace.type()) {
          case "LOGICAL" -> ExpressionTraceNode.NodeType.LOGICAL;
          case "COMPARISON" -> ExpressionTraceNode.NodeType.COMPARISON;
          case "ARITHMETIC" -> ExpressionTraceNode.NodeType.ARITHMETIC;
          case "VARIABLE" -> ExpressionTraceNode.NodeType.VARIABLE;
          case "FUNCTION_CALL" -> ExpressionTraceNode.NodeType.FUNCTION_CALL;
          case "TERNARY" -> ExpressionTraceNode.NodeType.TERNARY;
          default -> ExpressionTraceNode.NodeType.ROOT;
        };

    List<ExpressionTraceNode> children = new ArrayList<>();
    if (trace.children() != null) {
      for (ExprTraceBuilder.TraceNode child : trace.children()) {
        children.add(convertTraceTree(child, child.expression(), null, 0));
      }
    }

    return ExpressionTraceNode.builder()
        .nodeType(nodeType)
        .expression(trace.expression() != null ? trace.expression() : expression)
        .operator(trace.operator())
        .result(result != null ? result : trace.result())
        .variableName(trace.type().equals("VARIABLE") ? trace.expression() : null)
        .variableValue(trace.type().equals("VARIABLE") ? trace.result() : null)
        .shortCircuited(trace.shortCircuited())
        .elapsedNanos(elapsedNanos > 0 ? elapsedNanos : trace.elapsedNanos())
        .children(children)
        .error(trace.error())
        .build();
  }

  /**
   * 推断函数分类
   *
   * <p>优先使用注册表中注册的分类信息（P1-2 注解化后内置函数已在注册时声明分类）。 对于未注册分类的自定义函数，默认返回 "custom"。
   *
   * @param funcName 函数名
   * @return 函数分类字符串
   */
  private String inferCategory(String funcName) {
    String category = functionRegistry.getCategory(funcName);
    return category != null ? category : "custom";
  }
}
