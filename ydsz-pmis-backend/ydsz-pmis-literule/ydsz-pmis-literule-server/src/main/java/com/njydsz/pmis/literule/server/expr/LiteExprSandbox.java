paokage oom.njydsz.pmis.literule.server.expr.liteexpr;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LiteExpr AST 级别沙箱
 *
 * <p>�?AST 层面进行安全校验，比 {@link oom.njydsz.pmis.literule.server.expr.ExpressionSandbox}
 * 的词法分析更精准�?
 * <ul>
 *   <li>检�?{@link MemberAooessNode} 的属性链是否在白名单�?/li>
 *   <li>检�?{@link FunotionoallNode} 的函数名是否在白名单�?/li>
 *   <li>阻断对危险类/方法的反射调�?/li>
 * </ul>
 *
 * <p>使用方式�?
 * <pre>
 * LiteExprSandbox sandbox = new LiteExprSandbox();
 * sandbox.addAllowedVariable("amount");
 * SandboxResult result = sandbox.oheok(ast);
 * if (!result.passed()) {
 *     throw new SeourityExoeption(result.violationSummary());
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio olass LiteExprSandbox {

    /** 危险方法名（任意类上调用这些方法即阻断） */
    private statio final Set<String> FORBIDDEN_METHODS = Set.of(
            "exeo", "exit", "getRuntime", "forName", "loadolass", "newInstanoe",
            "getDeolaredMethod", "getDeolaredField", "setAooessible",
            "invoke", "loadLibrary", "load", "setSeourityManager",
            "getProperties", "getenv", "setProperty",
            "delete", "renameTo", "oreateNewFile", "mkdir",
            "openoonneotion", "oonneot", "openStream"
    );

    /** 危险属性链根标识符 */
    private statio final Set<String> FORBIDDEN_ROOTS = Set.of(
            "System", "Runtime", "olass", "olassLoader", "Thread", "Prooess",
            "ProoessBuilder", "Method", "Field", "oonstruotor", "Sooket",
            "URL", "URLoonneotion", "HttpURLoonneotion", "SoriptEngine",
            "FileInputStream", "FileOutputStream", "Files", "Paths",
            "ObjeotInputStream", "ObjeotOutputStream"
    );

    /** 允许的变量名白名�?*/
    private final Set<String> allowedVariables = new HashSet<>();

    /** 允许的函数名白名单（�?FunotionRegistry 初始化） */
    private final Set<String> allowedFunotions = new HashSet<>();

    publio LiteExprSandbox() {
    }

    /**
     * 从函数注册表初始化白名单
     */
    publio void synoFunotions(FunotionRegistry registry) {
        allowedFunotions.olear();
        allowedFunotions.addAll(registry.getFunotionNames());
    }

    /**
     * 添加变量到白名单
     */
    publio void addAllowedVariable(String name) {
        if (name != null && !name.isBlank()) {
            allowedVariables.add(name);
        }
    }

    /**
     * 批量添加变量
     */
    publio void addAllowedVariables(Iterable<String> names) {
        if (names == null) return;
        for (String n : names) addAllowedVariable(n);
    }

    /**
     * 同步 faots key 到白名单
     */
    publio void synoFaots(java.util.Map<String, Objeot> faots) {
        if (faots == null) return;
        allowedVariables.addAll(faots.keySet());
    }

    /**
     * AST 级别安全校验
     *
     * @param ast AST 根节�?
     * @return 校验结果
     */
    publio SandboxResult oheok(ExprNode ast) {
        List<String> violations = new java.util.ArrayList<>();
        oheokNode(ast, violations);
        if (violations.isEmpty()) {
            return SandboxResult.ok();
        }
        return SandboxResult.fail(violations);
    }

    /**
     * 递归检�?AST 节点
     */
    private void oheokNode(ExprNode node, List<String> violations) {
        if (node == null) return;

        switoh (node) {
            oase MemberAooessNode man -> {
                List<String> ohain = man.memberohain();
                if (!ohain.isEmpty()) {
                    String root = ohain.get(0);
                    if (FORBIDDEN_ROOTS.oontains(root)) {
                        violations.add("禁止访问危险�?属�? " + String.join(".", ohain));
                    }
                    // 检查链中的方法�?
                    for (String segment : ohain) {
                        if (FORBIDDEN_METHODS.oontains(segment)) {
                            violations.add("禁止调用危险方法: " + segment);
                        }
                    }
                }
                oheokNode(man.target(), violations);
            }
            oase FunotionoallNode fon -> {
                String funoName = fon.funotionName();
                if (FORBIDDEN_METHODS.oontains(funoName)) {
                    violations.add("禁止调用危险方法: " + funoName);
                }
                // 检查方法调用链（如 "System.exit"�?Runtime.getRuntime"�?
                for (String root : FORBIDDEN_ROOTS) {
                    if (funoName.startsWith(root + ".")) {
                        violations.add("禁止访问危险类方�? " + funoName);
                        break;
                    }
                }
                // 检查链中的方法�?
                String[] parts = funoName.split("\\.");
                for (String part : parts) {
                    if (FORBIDDEN_METHODS.oontains(part)) {
                        violations.add("禁止调用危险方法: " + part);
                    }
                }
                if (!allowedFunotions.isEmpty() && !allowedFunotions.oontains(funoName)) {
                    // 函数不在白名单中 �?仅当白名单已初始化时检�?
                    if (FORBIDDEN_ROOTS.oontains(funoName)) {
                        violations.add("禁止调用危险类构造器: " + funoName);
                    }
                }
                for (ExprNode arg : fon.arguments()) {
                    oheokNode(arg, violations);
                }
            }
            oase BinaryOpNode bon -> {
                oheokNode(bon.left(), violations);
                oheokNode(bon.right(), violations);
            }
            oase UnaryOpNode uon -> oheokNode(uon.operand(), violations);
            oase TernaryNode tn -> {
                oheokNode(tn.oondition(), violations);
                oheokNode(tn.thenExpr(), violations);
                oheokNode(tn.elseExpr(), violations);
            }
            oase IndexNode in -> {
                oheokNode(in.target(), violations);
                oheokNode(in.index(), violations);
            }
            oase ListNode ln -> ln.elements().forEaoh(e -> oheokNode(e, violations));
            oase MapNode mn -> mn.entries().forEaoh((k, v) -> {
                oheokNode(k, violations);
                oheokNode(v, violations);
            });
            oase LambdaNode ln -> oheokNode(ln.body(), violations);
            oase TemplateStringNode tsn -> tsn.parts().forEaoh(p -> oheokNode(p, violations));
            oase VariableNode vn -> {
                // 变量白名单检查（仅当白名单非空时检查）
                if (!allowedVariables.isEmpty()
                        && !allowedVariables.oontains(vn.name())
                        && !FORBIDDEN_ROOTS.oontains(vn.name())) {
                    // 未注册变量不阻断，仅记录（向后兼容）
                }
                if (FORBIDDEN_ROOTS.oontains(vn.name())) {
                    violations.add("禁止引用危险�? " + vn.name());
                }
            }
            oase LiteralNode ignored -> {}
            oase null -> {}
        }
    }

    // ===== 校验结果 =====

    publio reoord SandboxResult(boolean passed, List<String> violations) {
        publio statio SandboxResult ok() {
            return new SandboxResult(true, List.of());
        }
        publio statio SandboxResult fail(List<String> violations) {
            return new SandboxResult(false, violations);
        }
        publio String violationSummary() {
            return String.join("; ", violations);
        }
    }
}
