package com.njydsz.literule.server.engine.liteexpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.expression.ExpressionFunctionDef;
import com.njydsz.literule.domain.expression.ExpressionTraceNode;
import com.njydsz.literule.domain.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.vo.RuleContextVO;

/**
 * LiteExpr 自研表达式求值器
 *
 * <p>实现 {@link ExpressionEngine} 接口，对接上层规则引擎。 完全自研实现，核心组件：
 *
 * <ul>
 *   <li>{@link LiteExprCompiler} — 编译缓存 + 常量折叠 + 变量提取
 *   <li>{@link TreeInterpreter} — AST 遍历执行 + 短路求值 + 追踪树
 *   <li>{@link BytecodeCompiler} — AST 到字节码编译（P0-2 字节码编译能力）
 *   <li>{@link BytecodeInterpreter} — 栈式虚拟机字节码执行引擎
 *   <li>{@link FunctionRegistry} — 内置函数 + 业务函数注册
 *   <li>{@link LiteExprSandbox} — AST 级安全沙箱
 * </ul>
 *
 * <p>字节码编译作为可选执行路径：启用后，引擎优先尝试将 AST 编译为字节码执行（性能更优）；
 * 遇到不支持的语法结构时自动降级为树遍历解释器（向后兼容）。
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

  /**
   * 默认单次表达式求值超时（P0-6）
   *
   * <p>与 {@code ScriptRule.SANDBOX_TIMEOUT_MS=5000} 对齐，防止恶意/病态表达式无限执行。
   * 可通过构造器覆盖（如从配置注入）。
   */
  public static final long DEFAULT_MAX_EVAL_NANOS = 5_000_000_000L;

  private final LiteExprCompiler compiler;
  private final FunctionRegistry functionRegistry;
  private final TreeInterpreter interpreter;
  private final LiteExprSandbox sandbox;
  private final boolean sandboxEnabled;

  /** 字节码编译器（P0-2） */
  private final BytecodeCompiler bytecodeCompiler;

  /** 字节码解释器（P0-2） */
  private final BytecodeInterpreter bytecodeInterpreter;

  /** 是否启用字节码编译执行（P0-2） */
  private final boolean bytecodeEnabled;

  /** 编译后的字节码程序缓存（P0-2：避免重复编译同一表达式） */
  private final java.util.concurrent.ConcurrentHashMap<String, CompiledProgram> bytecodeCache =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** 单次表达式求值超时（纳秒）；0=不启用墙上时钟超时（节点预算仍生效） */
  private final long maxEvalNanos;

  public LiteExprEngine() {
    this(true);
  }

  public LiteExprEngine(boolean sandboxEnabled) {
    this(sandboxEnabled, DEFAULT_MAX_EVAL_NANOS);
  }

  /**
   * 带求值超时的引擎构造器
   *
   * @param sandboxEnabled 是否启用 AST 级沙箱
   * @param maxEvalNanos 单次表达式求值超时（纳秒）；0 或负数 = 不启用墙上时钟超时
   */
  public LiteExprEngine(boolean sandboxEnabled, long maxEvalNanos) {
    this(sandboxEnabled, maxEvalNanos, false);
  }

  /**
   * 完整构造器（含字节码编译开关，P0-2）
   *
   * @param sandboxEnabled 是否启用 AST 级沙箱
   * @param maxEvalNanos 单次表达式求值超时（纳秒）
   * @param bytecodeEnabled 是否启用字节码编译执行
   */
  public LiteExprEngine(boolean sandboxEnabled, long maxEvalNanos, boolean bytecodeEnabled) {
    this.sandboxEnabled = sandboxEnabled;
    this.maxEvalNanos = Math.max(0L, maxEvalNanos);
    this.bytecodeEnabled = bytecodeEnabled;
    this.compiler = new LiteExprCompiler();
    this.functionRegistry = new FunctionRegistry();
    this.interpreter = new TreeInterpreter(functionRegistry);
    this.sandbox = new LiteExprSandbox();
    this.sandbox.syncFunctions(functionRegistry);
    this.bytecodeCompiler = new BytecodeCompiler("");
    this.bytecodeInterpreter = new BytecodeInterpreter(functionRegistry);
    log.info(
        "[LiteExpr] 自研表达式引擎已初始化（sandbox={}, bytecode={}, functions={}, "
            + "cacheCapacity={}, maxEvalNanos={})",
        sandboxEnabled,
        bytecodeEnabled,
        functionRegistry.getFunctionNames().size(),
        CACHE_CAPACITY,
        this.maxEvalNanos);
  }

  @Override
  public boolean evalBoolean(String expression, RuleContextVO context) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    try {
      ExprNode ast = compileAndCheck(expression);
      Map<String, Object> facts = context != null ? context.getFacts() : Map.of();
      // P0-2：字节码编译执行路径
      Object result;
      if (bytecodeEnabled) {
        result = evalWithBytecode(expression, facts);
      } else {
        result = interpreter.eval(ast, facts, maxEvalNanos);
      }
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
      // 沙箱拦截 = 表达式内容本身存在风险，属配置事故，须 ERROR 级可观测（P0-8）
      log.error("[LiteExpr] 安全拦截（请立即检查表达式内容）: expr='{}', error={}", expression, e.getMessage());
      return false;
    } catch (LiteExprException e) {
      // 语法/语义/超时/深度超限 = 表达式配置缺陷，须 ERROR 级可观测（P0-8），
      // 与"条件不成立（返回 false）"区分开；契约保持 false=未命中不变
      log.error(
          "[LiteExpr] 表达式配置错误: expr='{}', line={}, col={}, error={}",
          expression,
          e.getLine(),
          e.getColumn(),
          e.getMessage());
      return false;
    } catch (Exception e) {
      log.warn("[LiteExpr] 布尔表达式求值失败: expr='{}', error={}", expression, e.getMessage());
      return false;
    }
  }

  @Override
  public Object eval(String expression, RuleContextVO context) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    try {
      ExprNode ast = compileAndCheck(expression);
      Map<String, Object> facts = context != null ? context.getFacts() : Map.of();
      // P0-2：字节码编译执行路径（优先尝试，失败降级到树遍历）
      if (bytecodeEnabled) {
        return evalWithBytecode(expression, facts);
      }
      return interpreter.eval(ast, facts, maxEvalNanos);
    } catch (SecurityException e) {
      // 沙箱拦截 = 表达式内容本身存在风险，属配置事故，须 ERROR 级可观测（P0-8）
      log.error("[LiteExpr] 安全拦截（请立即检查表达式内容）: expr='{}', error={}", expression, e.getMessage());
      return null;
    } catch (LiteExprException e) {
      // 语法/语义/超时/深度超限 = 表达式配置缺陷，须 ERROR 级可观测（P0-8）
      log.error(
          "[LiteExpr] 表达式配置错误: expr='{}', line={}, col={}, error={}",
          expression,
          e.getLine(),
          e.getColumn(),
          e.getMessage());
      return null;
    } catch (Exception e) {
      log.warn("[LiteExpr] 表达式求值失败: expr='{}', error={}", expression, e.getMessage());
      return null;
    }
  }

  /**
   * 使用字节码编译执行表达式（P0-2）
   *
   * <p>优先从缓存获取编译后的字节码程序；未命中则尝试编译并缓存。
   * 编译失败（遇到不支持的语法结构）时降级为树遍历解释器。
   *
   * @param expression 表达式文本
   * @param facts 变量上下文
   * @return 执行结果
   */
  private Object evalWithBytecode(String expression, Map<String, Object> facts) {
    CompiledProgram program = bytecodeCache.get(expression);
    if (program == null) {
      try {
        ExprNode ast = compiler.compile(expression);
        program = bytecodeCompiler.compile(ast);
        bytecodeCache.put(expression, program);
      } catch (LiteExprException e) {
        // 字节码编译不支持的语法结构，降级为树遍历
        log.warn("[LiteExpr] 字节码编译降级为树遍历: expr='{}', reason={}", expression, e.getMessage(), e);
        ExprNode ast = compiler.compile(expression);
        return interpreter.eval(ast, facts, maxEvalNanos);
      } catch (Exception e) {
        // 其他异常也降级为树遍历
        log.warn("[LiteExpr] 字节码编译异常降级为树遍历: expr='{}', reason={}", expression, e.getMessage(), e);
        ExprNode ast = compiler.compile(expression);
        return interpreter.eval(ast, facts, maxEvalNanos);
      }
    }
    return bytecodeInterpreter.execute(program, facts);
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
  public TraceResult evalBooleanWithTrace(String expression, RuleContextVO context) {
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
      Map<String, Object> facts = context != null ? context.getFacts() : Map.of();
      TreeInterpreter.TraceEvalResult traceResult =
          interpreter.evalWithTrace(ast, facts, maxEvalNanos);
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

  /** 清空编译缓存 + 沙箱校验缓存 + 字节码缓存（P0-2） */
  public void clearCache() {
    compiler.clearCache();
    sandbox.clearCache();
    bytecodeCache.clear();
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
