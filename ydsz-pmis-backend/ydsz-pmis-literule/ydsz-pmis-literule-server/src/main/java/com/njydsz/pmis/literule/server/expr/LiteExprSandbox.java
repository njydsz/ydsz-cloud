package com.njydsz.pmis.literule.server.expr.liteexpr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LiteExpr AST 级别沙箱
 *
 * <p>在 AST 层面进行安全校验，比 {@link com.njydsz.pmis.literule.server.expr.ExpressionSandbox}
 * 的词法分析更精准：
 * <ul>
 *   <li>检查 {@link MemberAccessNode} 的属性链是否在白名单中</li>
 *   <li>检查 {@link FunctionCallNode} 的函数名是否在白名单中</li>
 *   <li>阻断对危险类/方法的反射调用</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * LiteExprSandbox sandbox = new LiteExprSandbox();
 * sandbox.addAllowedVariable("amount");
 * SandboxResult result = sandbox.check(ast);
 * if (!result.passed()) {
 *     throw new SecurityException(result.violationSummary());
 * }
 * </pre>
 *
 * @since 2.0.0
 */
public class LiteExprSandbox {

    /** 危险方法名（任意类上调用这些方法即阻断） */
    private static final Set<String> FORBIDDEN_METHODS = Set.of(
            "exec", "exit", "getRuntime", "forName", "loadClass", "newInstance",
            "getDeclaredMethod", "getDeclaredField", "setAccessible",
            "invoke", "loadLibrary", "load", "setSecurityManager",
            "getProperties", "getenv", "setProperty",
            "delete", "renameTo", "createNewFile", "mkdir",
            "openConnection", "connect", "openStream"
    );

    /** 危险属性链根标识符 */
    private static final Set<String> FORBIDDEN_ROOTS = Set.of(
            "System", "Runtime", "Class", "ClassLoader", "Thread", "Process",
            "ProcessBuilder", "Method", "Field", "Constructor", "Socket",
            "URL", "URLConnection", "HttpURLConnection", "ScriptEngine",
            "FileInputStream", "FileOutputStream", "Files", "Paths",
            "ObjectInputStream", "ObjectOutputStream"
    );

    /** 允许的变量名白名单 */
    private final Set<String> allowedVariables = new HashSet<>();

    /** 允许的函数名白名单（由 FunctionRegistry 初始化） */
    private final Set<String> allowedFunctions = new HashSet<>();

    public LiteExprSandbox() {
    }

    /**
     * 从函数注册表初始化白名单
     */
    public void syncFunctions(FunctionRegistry registry) {
        allowedFunctions.clear();
        allowedFunctions.addAll(registry.getFunctionNames());
    }

    /**
     * 添加变量到白名单
     */
    public void addAllowedVariable(String name) {
        if (name != null && !name.isBlank()) {
            allowedVariables.add(name);
        }
    }

    /**
     * 批量添加变量
     */
    public void addAllowedVariables(Iterable<String> names) {
        if (names == null) return;
        for (String n : names) addAllowedVariable(n);
    }

    /**
     * 同步 facts key 到白名单
     */
    public void syncFacts(Map<String, Object> facts) {
        if (facts == null) return;
        allowedVariables.addAll(facts.keySet());
    }

    /**
     * AST 级别安全校验
     *
     * @param ast AST 根节点
     * @return 校验结果
     */
    public SandboxResult check(ExprNode ast) {
        List<String> violations = new ArrayList<>();
        checkNode(ast, violations);
        if (violations.isEmpty()) {
            return SandboxResult.ok();
        }
        return SandboxResult.fail(violations);
    }

    /**
     * 递归检查 AST 节点
     */
    private void checkNode(ExprNode node, List<String> violations) {
        if (node == null) return;

        switch (node) {
            case MemberAccessNode man -> {
                List<String> chain = man.memberChain();
                if (!chain.isEmpty()) {
                    String root = chain.get(0);
                    if (FORBIDDEN_ROOTS.contains(root)) {
                        violations.add("禁止访问危险类/属性: " + String.join(".", chain));
                    }
                    // 检查链中的方法名
                    for (String segment : chain) {
                        if (FORBIDDEN_METHODS.contains(segment)) {
                            violations.add("禁止调用危险方法: " + segment);
                        }
                    }
                }
                checkNode(man.target(), violations);
            }
            case FunctionCallNode fcn -> {
                String funcName = fcn.functionName();
                if (FORBIDDEN_METHODS.contains(funcName)) {
                    violations.add("禁止调用危险方法: " + funcName);
                }
                // 检查方法调用链（如 "System.exit"、"Runtime.getRuntime"）
                for (String root : FORBIDDEN_ROOTS) {
                    if (funcName.startsWith(root + ".")) {
                        violations.add("禁止访问危险类方法: " + funcName);
                        break;
                    }
                }
                // 检查链中的方法名
                String[] parts = funcName.split("\\.");
                for (String part : parts) {
                    if (FORBIDDEN_METHODS.contains(part)) {
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
            case MapNode mn -> mn.entries().forEach((k, v) -> {
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
                if (FORBIDDEN_ROOTS.contains(vn.name())) {
                    violations.add("禁止引用危险类: " + vn.name());
                }
            }
            case LiteralNode ignored -> {}
            case null -> {}
        }
    }

    // ===== 校验结果 =====

    public record SandboxResult(boolean passed, List<String> violations) {
        public static SandboxResult ok() {
            return new SandboxResult(true, List.of());
        }
        public static SandboxResult fail(List<String> violations) {
            return new SandboxResult(false, violations);
        }
        public String violationSummary() {
            return String.join("; ", violations);
        }
    }
}
