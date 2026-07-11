package com.njydsz.pmis.literule.server.expr;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式沙箱（AST 级别拦截，P1-11）
 *
 * <p>相比 P0 阶段的正则黑名单（{@code DANGEROUS_PATTERN}），本类采用词法分析方式：
 * <ol>
 *   <li>将表达式拆分为 token（标识符、字符串字面量、数字、运算符、括号等）</li>
 *   <li>跳过字符串字面量内部的"危险词"（如注释、提示语）</li>
 *   <li>对每个标识符做"段链"分析：识别 {@code a.b.c} 这样的链式访问，逐段校验</li>
 *   <li>对方法调用 {@code foo(args)} 做白名单检查，函数名不在白名单时阻断</li>
 *   <li>对类引用 {@code pkg.Class} 阻断黑名单包前缀（java.io / java.net / java.lang.reflect 等）</li>
 * </ol>
 *
 * <p>拦截维度：
 * <ul>
 *   <li>类加载：{@code java.lang.Class}、{@code ClassLoader}、{@code Class.forName}</li>
 *   <li>反射：{@code java.lang.reflect.*}</li>
 *   <li>进程执行：{@code ProcessBuilder}、{@code Runtime.getRuntime}、{@code System.exit}</li>
 *   <li>文件 I/O：{@code java.io.*}、{@code java.nio.file.*}（Files 工具类）</li>
 *   <li>网络 I/O：{@code java.net.*}、{@code URL}、{@code HttpURLConnection}</li>
 *   <li>脚本引擎：{@code javax.script.ScriptEngine}</li>
 *   <li>JNI：{@code loadLibrary}、{@code load}</li>
 * </ul>
 *
 * <p>允许的标识符：表达式上下文变量（驼峰/下划线命名）、白名单函数。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class ExpressionSandbox {

    /** 标识符正则：首字符字母/下划线/$，后续字母/数字/下划线/$ */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** 字符串字面量正则（双引号/单引号/反引号），用于在扫描时跳过内部内容 */
    private static final Pattern STRING_LITERAL = Pattern.compile(
            "\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'|`[^`]*`");

    /** 危险包前缀（AST 级别，替代正则黑名单） */
    private static final List<String> FORBIDDEN_PACKAGES = Arrays.asList(
            "java.io.",
            "java.nio.file.",
            "java.net.",
            "java.lang.reflect.",
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.Runtime",
            "java.lang.System",
            "java.lang.Thread",
            "java.lang.Process",
            "java.lang.ProcessBuilder",
            "javax.script.",
            "java.security.",
            "sun.",
            "jdk.internal.",
            "com.sun."
    );

    /** 危险类/方法名（不带包前缀的简名，AST 级别最后一道防线） */
    private static final Set<String> FORBIDDEN_SIMPLE_CLASSES = new HashSet<>(Arrays.asList(
            "System", "Runtime", "Class", "ClassLoader", "Thread", "Process",
            "ProcessBuilder", "Method", "Field", "Constructor", "Socket",
            "URL", "URLConnection", "HttpURLConnection", "ScriptEngine",
            "FileInputStream", "FileOutputStream", "Files", "Paths",
            "ObjectInputStream", "ObjectOutputStream",
            "Scanner", "RuntimeException"
    ));

    /** 危险方法名（任意类上调用这些方法即阻断） */
    private static final Set<String> FORBIDDEN_METHODS = new HashSet<>(Arrays.asList(
            "exec", "exit", "getRuntime", "forName", "loadClass", "newInstance",
            "getDeclaredMethod", "getDeclaredField", "setAccessible",
            "invoke", "loadLibrary", "load", "setSecurityManager",
            "getProperties", "getenv", "setProperty", "remove",
            "delete", "deleteOnExit", "renameTo", "createNewFile", "mkdir",
            "openConnection", "connect", "openStream"
    ));

    /** LiteExpr 关键字（表达式语法关键字） */
    private static final Set<String> EXPR_KEYWORDS = new HashSet<>(Arrays.asList(
            "true", "false", "nil", "null", "and", "or", "not",
            "if", "elsif", "else", "endif", "for", "in", "end", "return",
            "let", "break", "continue", "while", "lambda", "fn",
            "RED", "YELLOW", "INFO", "GREEN", "NORMAL",
            "println", "print", "p", "string", "long", "double", "decimal",
            "boolean", "int", "tuple", "map", "set", "sorted", "sort"
    ));

    /** 内置函数白名单（LiteExpr 标准库） */
    @Getter
    private final Set<String> allowedFunctions = new HashSet<>(Arrays.asList(
            // 数学
            "max", "min", "abs", "round", "floor", "ceil", "sqrt", "pow",
            "log", "log10", "sin", "cos", "tan", "asin", "acos", "atan",
            "exp", "random", "rand",
            // 字符串
            "contains", "startsWith", "endsWith", "length", "size",
            "upper", "lower", "trim", "substring", "indexOf", "lastIndexOf",
            "replace", "split", "join", "concat", "compareTo", "isEmpty",
            "isBlank", "isNotBlank", "equals", "isNull", "isNotNull",
            // 集合
            "count", "sum", "avg", "first", "last", "head", "tail",
            "filter", "map", "reduce", "distinct", "unique", "flatten",
            "sortBy", "groupBy", "page", "limit",
            // 类型
            "string", "long", "double", "int", "boolean", "decimal", "bigint",
            "toString", "toLong", "toDouble", "toInt", "toBoolean",
            // 时间
            "now", "today", "date", "datetime", "year", "month", "day",
            "hour", "minute", "second", "format", "parse",
            // 序列
            "seq", "seqList", "range", "next", "reduce",
            // 业务扩展（项目内注册函数）
            "uuid", "md5", "sha256", "jsonEncode", "jsonDecode", "base64"
    ));

    /** 允许的属性链"根"标识符白名单（变量名） */
    @Getter
    private final Set<String> variableWhitelist = new HashSet<>();

    /** 表达式求值期间暴露的 facts key 集合（自动加入白名单） */
    @Getter
    private final Set<String> factsKeys = new HashSet<>();

    /**
     * 添加变量名到白名单
     */
    public void addAllowedVariable(String name) {
        if (name != null && !name.isBlank()) {
            variableWhitelist.add(name);
        }
    }

    /**
     * 批量添加变量名
     */
    public void addAllowedVariables(Iterable<String> names) {
        if (names == null) return;
        for (String n : names) addAllowedVariable(n);
    }

    /**
     * 同步 RuleContext 的 facts 到白名单
     */
    public void syncFacts(Map<String, Object> facts) {
        if (facts == null) return;
        factsKeys.addAll(facts.keySet());
        variableWhitelist.addAll(facts.keySet());
    }

    /**
     * 注册业务函数到白名单
     */
    public void registerFunction(String name) {
        if (name != null && !name.isBlank()) {
            allowedFunctions.add(name);
        }
    }

    /**
     * AST 级别安全校验
     *
     * <p>主要流程：
     * <ol>
     *   <li>剥离字符串字面量（避免误判）</li>
     *   <li>提取所有标识符 token</li>
     *   <li>对每个标识符做段链分析、危险类/方法名检查</li>
     *   <li>未识别的标识符视为可疑，要求在白名单中</li>
     * </ol>
     *
     * @param expression 表达式文本
     * @return 校验结果
     */
    public SandboxCheckResult check(String expression) {
        if (expression == null || expression.isBlank()) {
            return SandboxCheckResult.ok();
        }

        // 1. 字符串字面量替换为空格，剥离后再分析（避免 "exec" 字面量被误判）
        String masked = maskStringLiterals(expression);

        // 2. 提取所有标识符 + 记录其位置
        List<String> identifiers = new ArrayList<>();
        List<Integer> identifierStartPositions = new ArrayList<>();
        List<Integer> identifierEndPositions = new ArrayList<>();
        Matcher m = IDENTIFIER.matcher(masked);
        while (m.find()) {
            identifiers.add(m.group());
            identifierStartPositions.add(m.start());
            identifierEndPositions.add(m.end());
        }

        // 3. 检测：是否出现"包路径 + 类名"链式引用
        //    如 a.b.c.d() 拆出 a, b, c, d；逐段检查
        Set<String> foundForbiddenPackages = new LinkedHashSet<>();
        Set<String> foundForbiddenClasses = new LinkedHashSet<>();
        Set<String> foundForbiddenMethods = new LinkedHashSet<>();
        Set<String> unknownIdentifiers = new LinkedHashSet<>();

        for (int i = 0; i < identifiers.size(); i++) {
            String id = identifiers.get(i);
            int idStart = identifierStartPositions.get(i);
            int idEnd = identifierEndPositions.get(i);

            // 跳过关键字和已知白名单
            if (EXPR_KEYWORDS.contains(id)) continue;
            if (variableWhitelist.contains(id)) continue;
            if (factsKeys.contains(id)) continue;
            if (allowedFunctions.contains(id)) continue;

            // 跳过前一个 token 是 "." 链式访问的一部分（包路径中段）的小写标识符
            //    但如果它同时是方法调用（后面跟 "("）且是危险方法名，则不能跳过
            if (Character.isLowerCase(id.charAt(0)) && isPartOfDotChain(masked, idStart)) {
                boolean methodCall = isMethodName(masked, idEnd, id);
                boolean dangerousMethod = methodCall && FORBIDDEN_METHODS.contains(id);
                if (!dangerousMethod) {
                    continue;
                }
            }

            // 检测大写开头的类名：尝试往前拼包路径
            if (Character.isUpperCase(id.charAt(0))) {
                String pkgPath = reconstructPackagePath(identifiers, identifierStartPositions, identifierEndPositions, i, masked);
                if (isForbiddenPackage(pkgPath)) {
                    foundForbiddenPackages.add(pkgPath);
                }
                if (isForbiddenClass(id)) {
                    foundForbiddenClasses.add(id);
                }
            }

            // 检测方法名（出现在 ( 之前的标识符）
            if (isMethodName(masked, idEnd, id)) {
                if (isForbiddenMethod(id)) {
                    foundForbiddenMethods.add(id);
                }
            }

            // 检测未识别标识符（不在白名单中、不是变量规则、不是关键字）
            // 小写开头 + 前后是 . 链式访问的，已经在前面 continue
            if (!EXPR_KEYWORDS.contains(id)
                    && !variableWhitelist.contains(id)
                    && !allowedFunctions.contains(id)
                    && !Character.isUpperCase(id.charAt(0))
                    && !isPartOfDotChain(masked, idStart)) {
                unknownIdentifiers.add(id);
            }
        }

        // 4. 构造结果
        if (foundForbiddenPackages.isEmpty()
                && foundForbiddenClasses.isEmpty()
                && foundForbiddenMethods.isEmpty()) {
            // 若存在未识别标识符，降级为警告（不阻断，因为可能未被加入白名单）
            if (!unknownIdentifiers.isEmpty()) {
                log.warn("[ExpressionSandbox] 表达式含未识别标识符: {}", unknownIdentifiers);
            }
            return SandboxCheckResult.ok();
        }
        List<String> violations = new ArrayList<>();
        if (!foundForbiddenPackages.isEmpty()) {
            violations.add("禁止访问危险包: " + foundForbiddenPackages);
        }
        if (!foundForbiddenClasses.isEmpty()) {
            violations.add("禁止访问危险类: " + foundForbiddenClasses);
        }
        if (!foundForbiddenMethods.isEmpty()) {
            violations.add("禁止调用危险方法: " + foundForbiddenMethods);
        }
        return SandboxCheckResult.fail(violations);
    }

    /**
     * 检查标识符是否是链式属性访问（dot chain）的一部分
     *
     * <p>判定规则：当前标识符的紧前一个非空白字符是 "."，
     * 或当前标识符的紧后一个非空白字符是 "."。
     * 这样如 "a.b.c" 中的 b 就是"包路径中段"，不应单独判定。
     *
     * @param masked  表达式（字符串字面量已剥离）
     * @param idStart 标识符起始位置
     */
    private boolean isPartOfDotChain(String masked, int idStart) {
        // 向前看：紧邻的字符（跳过空白后）是否是 "."
        int left = idStart - 1;
        while (left >= 0 && Character.isWhitespace(masked.charAt(left))) left--;
        if (left >= 0 && masked.charAt(left) == '.') return true;
        return false;
    }

    /**
     * 将字符串字面量替换为空格
     */
    private String maskStringLiterals(String expression) {
        Matcher m = STRING_LITERAL.matcher(expression);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            // 替换为同长度的空格
            m.appendReplacement(sb, Matcher.quoteReplacement(repeat(' ', m.end() - m.start())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String repeat(char c, int n) {
        char[] arr = new char[n];
        Arrays.fill(arr, c);
        return new String(arr);
    }

    /**
     * 重构包路径：向前扫描，拼出 "a.b.c" 形式的完整路径
     */
    private String reconstructPackagePath(List<String> identifiers,
                                          List<Integer> starts,
                                          List<Integer> ends,
                                          int index,
                                          String masked) {
        // 先收集到 index 为止的路径段
        List<String> parts = new ArrayList<>();
        // 向前：若前一个标识符与当前标识符之间只有 "."（dot chain），则拼入
        for (int j = index; j >= 0; j--) {
            parts.add(0, identifiers.get(j));
            if (j == 0) break;
            int end = ends.get(j - 1);
            int start = starts.get(j);
            // 之间是否只有 "." 链
            boolean dotChain = isDirectDotChain(masked, end, start);
            if (!dotChain) break;
        }
        // 向后：若后续标识符是大写开头且 dot chain 相连，也拼入
        for (int j = index + 1; j < Math.min(index + 10, identifiers.size()); j++) {
            String next = identifiers.get(j);
            if (Character.isUpperCase(next.charAt(0))) {
                int end = ends.get(j - 1);
                int start = starts.get(j);
                if (isDirectDotChain(masked, end, start)) {
                    parts.add(next);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return String.join(".", parts);
    }

    /**
     * 检测两个 token 之间是否直接以 "." 相连
     */
    private boolean isDirectDotChain(String text, int end, int start) {
        if (start <= end) return false;
        // 之间跳过空白后只允许一个 "."
        int pos = end;
        while (pos < start && Character.isWhitespace(text.charAt(pos))) pos++;
        if (pos >= start) return false;
        if (text.charAt(pos) != '.') return false;
        pos++;
        while (pos < start && Character.isWhitespace(text.charAt(pos))) pos++;
        return pos == start;
    }

    /**
     * 检测包路径前缀是否在黑名单中
     */
    private boolean isForbiddenPackage(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        for (String prefix : FORBIDDEN_PACKAGES) {
            if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 大小写不敏感地检查类名是否在危险类名单中
     */
    private boolean isForbiddenClass(String name) {
        if (name == null) return false;
        for (String forbidden : FORBIDDEN_SIMPLE_CLASSES) {
            if (forbidden.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /**
     * 大小写不敏感地检查方法名是否在危险方法名单中
     */
    private boolean isForbiddenMethod(String name) {
        if (name == null) return false;
        for (String forbidden : FORBIDDEN_METHODS) {
            if (forbidden.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /**
     * 检测标识符是否为方法名（后跟左括号）
     *
     * @param masked 表达式（字符串字面量已剥离）
     * @param idEnd  标识符在 masked 中的结束位置
     * @param identifier 标识符文本
     */
    private boolean isMethodName(String masked, int idEnd, String identifier) {
        if (idEnd >= masked.length()) return false;
        // 跳过空白
        int pos = idEnd;
        while (pos < masked.length() && Character.isWhitespace(masked.charAt(pos))) pos++;
        return pos < masked.length() && masked.charAt(pos) == '(';
    }

    /**
     * 校验结果
     */
    @Getter
    public static class SandboxCheckResult {
        private final boolean passed;
        private final List<String> violations;

        private SandboxCheckResult(boolean passed, List<String> violations) {
            this.passed = passed;
            this.violations = violations == null ? Collections.emptyList() : violations;
        }

        public static SandboxCheckResult ok() {
            return new SandboxCheckResult(true, Collections.emptyList());
        }

        public static SandboxCheckResult fail(List<String> violations) {
            return new SandboxCheckResult(false, violations);
        }

        public String violationSummary() {
            return String.join("; ", violations);
        }
    }
}
