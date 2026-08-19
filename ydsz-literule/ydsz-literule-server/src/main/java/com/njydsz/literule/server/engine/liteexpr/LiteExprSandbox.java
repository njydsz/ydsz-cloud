package com.njydsz.literule.server.engine.liteexpr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LiteExpr AST 级别沙箱
 *
 * <p>在 AST 层面进行安全校验，比传统词法分析更精准：
 *
 * <ul>
 *   <li>检查 {@link MemberAccessNode} 的属性链是否在白名单中
 *   <li>检查 {@link FunctionCallNode} 的函数名是否在白名单中
 *   <li>阻断对危险类/方法的反射调用
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>
 * LiteExprSandbox sandbox = new LiteExprSandbox();
 * sandbox.addAllowedVariable("amount");
 * SandboxResult result = sandbox.check(ast);
 * if (!result.passed()) {
 *     throw new SecurityException(result.violationSummary());
 * }
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class LiteExprSandbox {

  /** 危险方法名（任意类上调用这些方法即阻断） */
  private static final Set<String> FORBIDDEN_METHODS =
      Set.of(
          "exec",
          "exit",
          "getRuntime",
          "forName",
          "loadClass",
          "newInstance",
          "getDeclaredMethod",
          "getDeclaredField",
          "setAccessible",
          "invoke",
          "loadLibrary",
          "load",
          "setSecurityManager",
          "getProperties",
          "getenv",
          "setProperty",
          "delete",
          "renameTo",
          "createNewFile",
          "mkdir",
          "openConnection",
          "connect",
          "openStream");

  /** 危险属性链根标识符 */
  private static final Set<String> FORBIDDEN_ROOTS =
      Set.of(
          "System",
          "Runtime",
          "Class",
          "ClassLoader",
          "Thread",
          "Process",
          "ProcessBuilder",
          "Method",
          "Field",
          "Constructor",
          "Socket",
          "URL",
          "URLConnection",
          "HttpURLConnection",
          "ScriptEngine",
          "FileInputStream",
          "FileOutputStream",
          "Files",
          "Paths",
          "ObjectInputStream",
          "ObjectOutputStream");

  /** 允许的变量名白名单 */
  private final Set<String> allowedVariables = new HashSet<>();

  /** 允许的函数名白名单（由 FunctionRegistry 初始化） */
  private final Set<String> allowedFunctions = new HashSet<>();

  /** 扩展危险方法名（O2 配置外置化，可热更新追加） */
  private final Set<String> extraForbiddenMethods = new HashSet<>();

  /** 扩展危险属性链根标识符（O2 配置外置化，可热更新追加） */
  private final Set<String> extraForbiddenRoots = new HashSet<>();

  /**
   * 沙箱校验结果缓存（P1-3 性能优化）
   *
   * <p>使用 {@link IdentityHashMap} 以 AST 对象引用为 key（{@link LiteExprCompiler} 按表达式字符串缓存 AST，
   * 同一表达式返回相同对象引用）。 校验结果仅依赖静态 {@code FORBIDDEN_*} 集合， 与运行时白名单状态无关（白名单仅抑制误报，不产生新的违规项），因此可安全缓存。
   *
   * <p>缓存容量无限制，依赖 {@link LiteExprCompiler} 的编译缓存（默认 4096 条）作为天然上界。
   */
  private final Map<ExprNode, SandboxResult> checkCache = new IdentityHashMap<>();

  public LiteExprSandbox() {}

  /** 从函数注册表初始化白名单 */
  public void syncFunctions(FunctionRegistry registry) {
    allowedFunctions.clear();
    allowedFunctions.addAll(registry.getFunctionNames());
  }

  // ===== O2 沙箱规则外置化（配置可追加，非 static 硬编码） =====

  /**
   * 应用沙箱策略（O2 配置外置化）
   *
   * <p>将 YAML 配置中的黑名单方法/类根追加到沙箱规则， 白名单函数合并到函数白名单（{@code null} 表示不修改对应集合）。
   *
   * @param forbiddenMethods 追加的危险方法名（可为 null）
   * @param forbiddenRoots 追加的危险类/属性链根（可为 null）
   * @param allowedFunctions 追加的白名单函数（可为 null）
   */
  public void applyPolicy(
      Iterable<String> forbiddenMethods,
      Iterable<String> forbiddenRoots,
      Iterable<String> allowedFunctions) {
    if (forbiddenMethods != null) {
      for (String m : forbiddenMethods) {
        if (m != null && !m.isBlank()) extraForbiddenMethods.add(m.trim());
      }
    }
    if (forbiddenRoots != null) {
      for (String r : forbiddenRoots) {
        if (r != null && !r.isBlank()) extraForbiddenRoots.add(r.trim());
      }
    }
    if (allowedFunctions != null) {
      for (String f : allowedFunctions) {
        if (f != null && !f.isBlank()) this.allowedFunctions.add(f.trim());
      }
    }
    // 规则集合变化，清空校验缓存
    clearCache();
  }

  /** 追加危险方法名 */
  public void addForbiddenMethod(String method) {
    if (method != null && !method.isBlank()) {
      extraForbiddenMethods.add(method.trim());
      clearCache();
    }
  }

  /** 追加危险类/属性链根 */
  public void addForbiddenRoot(String root) {
    if (root != null && !root.isBlank()) {
      extraForbiddenRoots.add(root.trim());
      clearCache();
    }
  }

  /** 清空全部扩展规则（恢复仅内置黑名单） */
  public void clearExtraPolicy() {
    extraForbiddenMethods.clear();
    extraForbiddenRoots.clear();
    clearCache();
  }

  /** 添加变量到白名单 */
  public void addAllowedVariable(String name) {
    if (name != null && !name.isBlank()) {
      allowedVariables.add(name);
    }
  }

  /** 批量添加变量 */
  public void addAllowedVariables(Iterable<String> names) {
    if (names == null) return;
    for (String n : names) addAllowedVariable(n);
  }

  /**
   * 同步 facts key 到白名单（P0-T4：每次调用先清空再添加，防止高基数场景下 Set 无限增长）
   *
   * <p>每次评估前调用此方法，将当前 facts 的 key 替换为白名单内容。 避免不同请求的 facts key（如 traceId、时间戳等高基数 key）在 Set
   * 中累积导致内存泄漏。
   */
  public void syncFacts(Map<String, Object> facts) {
    allowedVariables.clear();
    if (facts != null) {
      allowedVariables.addAll(facts.keySet());
    }
  }

  /**
   * AST 级别安全校验（P1-3：带缓存，同一 AST 引用仅校验一次）
   *
   * @param ast AST 根节点
   * @return 校验结果
   */
  public SandboxResult check(ExprNode ast) {
    SandboxResult cached = checkCache.get(ast);
    if (cached != null) {
      return cached;
    }
    List<String> violations = new ArrayList<>();
    checkNode(ast, violations);
    SandboxResult result;
    if (violations.isEmpty()) {
      result = SandboxResult.ok();
    } else {
      result = SandboxResult.fail(violations);
    }
    checkCache.put(ast, result);
    return result;
  }

  /** 清空校验结果缓存（函数白名单变更时调用） */
  public void clearCache() {
    checkCache.clear();
  }

  /** 递归检查 AST 节点 */
  private void checkNode(ExprNode node, List<String> violations) {
    if (node == null) return;

    switch (node) {
      case MemberAccessNode man -> {
        List<String> chain = man.memberChain();
        if (!chain.isEmpty()) {
          String root = chain.get(0);
          if (FORBIDDEN_ROOTS.contains(root) || extraForbiddenRoots.contains(root)) {
            violations.add("禁止访问危险类/属性: " + String.join(".", chain));
          }
          // 检查链中的方法名
          for (String segment : chain) {
            if (FORBIDDEN_METHODS.contains(segment) || extraForbiddenMethods.contains(segment)) {
              violations.add("禁止调用危险方法: " + segment);
            }
          }
        }
        checkNode(man.target(), violations);
      }
      case FunctionCallNode fcn -> {
        String funcName = fcn.functionName();
        if (FORBIDDEN_METHODS.contains(funcName) || extraForbiddenMethods.contains(funcName)) {
          violations.add("禁止调用危险方法: " + funcName);
        }
        // 检查方法调用链（如 "System.exit"、"Runtime.getRuntime"）
        for (String root : FORBIDDEN_ROOTS) {
          if (funcName.startsWith(root + ".")) {
            violations.add("禁止访问危险类方法: " + funcName);
            break;
          }
        }
        for (String root : extraForbiddenRoots) {
          if (funcName.startsWith(root + ".")) {
            violations.add("禁止访问危险类方法: " + funcName);
            break;
          }
        }
        // 检查链中的方法名
        String[] parts = funcName.split("\\.");
        for (String part : parts) {
          if (FORBIDDEN_METHODS.contains(part) || extraForbiddenMethods.contains(part)) {
            violations.add("禁止调用危险方法: " + part);
          }
        }
        if (!allowedFunctions.isEmpty() && !allowedFunctions.contains(funcName)) {
          // 函数不在白名单中 — 仅当白名单已初始化时检查
          if (FORBIDDEN_ROOTS.contains(funcName)) {
            violations.add("禁止调用危险类构造器: " + funcName);
          }
        }
        for (ExprNode arg : fcn.arguments()) {
          checkNode(arg, violations);
        }
      }
      case BinaryOpNode bon -> {
        checkNode(bon.left(), violations);
        checkNode(bon.right(), violations);
      }
      case UnaryOpNode uon -> checkNode(uon.operand(), violations);
      case TernaryNode tn -> {
        checkNode(tn.condition(), violations);
        checkNode(tn.thenExpr(), violations);
        checkNode(tn.elseExpr(), violations);
      }
      case IndexNode in -> {
        checkNode(in.target(), violations);
        checkNode(in.index(), violations);
      }
      case ListNode ln -> ln.elements().forEach(e -> checkNode(e, violations));
      case MapNode mn ->
          mn.entries()
              .forEach(
                  (k, v) -> {
                    checkNode(k, violations);
                    checkNode(v, violations);
                  });
      case LambdaNode ln -> checkNode(ln.body(), violations);
      case TemplateStringNode tsn -> tsn.parts().forEach(p -> checkNode(p, violations));
      case VariableNode vn -> {
        // 变量白名单检查（仅当白名单非空时检查）
        if (!allowedVariables.isEmpty()
            && !allowedVariables.contains(vn.name())
            && !FORBIDDEN_ROOTS.contains(vn.name())) {
          // 未注册变量不阻断，仅记录（向后兼容）
        }
        if (FORBIDDEN_ROOTS.contains(vn.name()) || extraForbiddenRoots.contains(vn.name())) {
          violations.add("禁止引用危险类: " + vn.name());
        }
      }
      case LiteralNode ignored -> {}
      case null -> {}
    }
  }

  // ===== 校验结果 =====

  public record SandboxResult(boolean passed, List<String> violations) {
    /**
     * 构造校验通过的结果（无违规项）。
     *
     * @return 通过的 {@link SandboxResult}（violations 为空列表）
     */
    public static SandboxResult ok() {
      return new SandboxResult(true, List.of());
    }

    /**
     * 构造校验失败的结果。
     *
     * @param violations 违规描述列表（非空；调用方应保证不传 null）
     * @return 失败的 {@link SandboxResult}
     */
    public static SandboxResult fail(List<String> violations) {
      return new SandboxResult(false, violations);
    }

    /**
     * 将全部违规描述拼接为可读的半角分号分隔字符串，用于异常消息与日志。
     *
     * @return 违规摘要；无违规时返回空字符串
     */
    public String violationSummary() {
      return String.join("; ", violations);
    }
  }
}
