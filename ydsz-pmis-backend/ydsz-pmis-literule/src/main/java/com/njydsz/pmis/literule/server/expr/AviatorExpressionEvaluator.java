package com.njydsz.pmis.literule.server.expr;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.njydsz.pmis.literule.api.RuleContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aviator 表达式求值器实现
 *
 * <p>使用 Aviator 编译缓存提升性能，线程安全。
 * 支持自定义函数注入（通过 {@link #addFunction} 扩展）。
 *
 * <p>沙箱模式（默认启用）：限制危险函数和 Java 类访问，防止恶意表达式执行系统命令、
 * 访问文件系统或反射调用敏感 API。可通过构造器参数或配置关闭。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class AviatorExpressionEvaluator implements ExpressionEvaluator {

    /** Aviator 实例（独立实例，避免污染全局） */
    private final AviatorEvaluatorInstance instance;

    /** 表达式编译缓存（表达式文本 -> 编译后的 Expression） */
    private final ConcurrentHashMap<String, Expression> cache = new ConcurrentHashMap<>();

    /** 是否启用沙箱 */
    private final boolean sandboxEnabled;

    /**
     * AST 级别表达式沙箱（P1-11，替代 P0 阶段的正则黑名单）
     */
    private final ExpressionSandbox sandbox = new ExpressionSandbox();

    public AviatorExpressionEvaluator() {
        this(true);
    }

    /**
     * 构造表达式求值器
     *
     * @param sandboxEnabled 是否启用沙箱（true=限制危险操作）
     * @since 1.3.0
     */
    public AviatorExpressionEvaluator(boolean sandboxEnabled) {
        this.instance = AviatorEvaluator.newInstance();
        this.sandboxEnabled = sandboxEnabled;
        // 浮点数解析为 Decimal 类型，避免精度丢失
        this.instance.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, true);

        if (sandboxEnabled) {
            configureSandbox();
            log.info("[LiteRule-Aviator] 表达式沙箱已启用（危险函数和类访问已限制）");
        } else {
            log.warn("[LiteRule-Aviator] 表达式沙箱已关闭，表达式可访问任意 Java 类（生产环境不推荐）");
        }
    }

    /**
     * 配置沙箱：禁用 Aviator 危险 Feature
     */
    private void configureSandbox() {
        // 禁用 NewInstance（防止通过 new 创建危险对象）
        instance.disableFeature(Feature.NewInstance);
        // 禁用 Module（防止访问 Java 模块系统）
        instance.disableFeature(Feature.Module);
        // 禁用 Lambda（防止通过 lambda 构造复杂逻辑绕过限制）
        instance.disableFeature(Feature.Lambda);
    }

    /**
     * 沙箱表达式安全校验
     *
     * <p>P1-11：使用 {@link ExpressionSandbox}（AST 级别词法分析）替代 P0 的正则黑名单。
     * 解析流程：
     * <ol>
     *   <li>剥离字符串字面量，避免误判</li>
     *   <li>提取所有标识符 token</li>
     *   <li>对链式包路径、危险类名、危险方法名做白名单/黑名单校验</li>
     * </ol>
     *
     * @param expression 表达式文本
     * @throws SecurityException 表达式包含危险操作
     */
    private void sandboxCheck(String expression) {
        if (!sandboxEnabled) {
            return;
        }
        ExpressionSandbox.SandboxCheckResult result = sandbox.check(expression);
        if (!result.isPassed()) {
            throw new SecurityException("表达式被沙箱拦截: " + result.violationSummary());
        }
    }

    @Override
    public boolean evalBoolean(String expression, RuleContext context) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            sandboxCheck(expression);
            Expression compiled = compile(expression);
            Object result = compiled.execute(context.getFacts());
            if (result instanceof Boolean b) return b;
            if (result instanceof Number n) return n.doubleValue() != 0;
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (SecurityException e) {
            log.warn("[LiteRule-Aviator] 安全拦截: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("[LiteRule-Aviator] 布尔表达式求值失败: expr='{}', error={}", expression, e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T eval(String expression, RuleContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            sandboxCheck(expression);
            Expression compiled = compile(expression);
            return (T) compiled.execute(context.getFacts());
        } catch (SecurityException e) {
            log.warn("[LiteRule-Aviator] 安全拦截: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[LiteRule-Aviator] 表达式求值失败: expr='{}', error={}", expression, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean validate(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            sandboxCheck(expression);
            compile(expression);
            return true;
        } catch (SecurityException e) {
            log.debug("[LiteRule-Aviator] 表达式被沙箱拦截: expr='{}', error={}", expression, e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("[LiteRule-Aviator] 表达式校验失败: expr='{}', error={}", expression, e.getMessage());
            return false;
        }
    }

    @Override
    public ExpressionValidationResult validateDetailed(String expression) {
        long start = System.nanoTime();
        long elapsed = (System.nanoTime() - start) / 1_000_000L;

        // 1. 空表达式
        if (expression == null || expression.isBlank()) {
            return ExpressionValidationResult.fail(expression,
                    ExpressionValidationResult.ErrorType.EMPTY,
                    "表达式为空", elapsed);
        }

        // 2. 沙箱拦截（在编译前检查危险模式）
        try {
            sandboxCheck(expression);
        } catch (SecurityException e) {
            elapsed = (System.nanoTime() - start) / 1_000_000L;
            return ExpressionValidationResult.fail(expression,
                    ExpressionValidationResult.ErrorType.SANDBOX_VIOLATION,
                    e.getMessage(), elapsed);
        }

        // 3. 编译校验
        try {
            instance.compile(expression, true);
        } catch (Exception e) {
            elapsed = (System.nanoTime() - start) / 1_000_000L;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            int line = -1;
            int column = -1;
            // 尝试从消息中解析位置（Aviator 消息格式：通常包含 "line N" 或 "[N,M]"）
            java.util.regex.Matcher lineMatcher = java.util.regex.Pattern
                    .compile("line\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(msg);
            if (lineMatcher.find()) {
                line = Integer.parseInt(lineMatcher.group(1));
            }
            java.util.regex.Matcher colMatcher = java.util.regex.Pattern
                    .compile("(?:column|col)\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(msg);
            if (colMatcher.find()) {
                column = Integer.parseInt(colMatcher.group(1));
            }

            return ExpressionValidationResult.builder()
                    .valid(false)
                    .errorType(ExpressionValidationResult.ErrorType.SYNTAX_ERROR)
                    .errorMessage(msg)
                    .errorLine(line)
                    .errorColumn(column)
                    .expression(expression)
                    .parseTimeMs(elapsed)
                    .referencedVariables(new ArrayList<>())
                    .build();
        }

        // 4. 编译通过，提取引用变量
        elapsed = (System.nanoTime() - start) / 1_000_000L;
        List<String> vars = extractVariables(expression);
        return ExpressionValidationResult.ok(expression, elapsed, vars);
    }

    /** Aviator 关键字与内置函数，不应作为变量返回 */
    private static final Set<String> AVIATOR_KEYWORDS = Set.of(
            "true", "false", "nil", "null",
            "RED", "YELLOW", "INFO", "GREEN",
            "if", "else", "return", "seq", "lambda", "fn",
            "let", "for", "while", "break", "continue",
            "println", "print", "p", "string", "long", "double",
            "boolean", "int", "math", "Math",
            "max", "min", "abs", "round", "floor", "ceil", "sqrt", "pow", "log",
            "contains", "startsWith", "endsWith", "length",
            "count", "sum", "avg", "rand", "now", "date",
            "tuple", "map", "set", "sorted", "sort"
    );

    /**
     * 从表达式中提取引用的变量名
     *
     * <p>基于正则提取标识符，过滤 Aviator 关键字与内置函数。
     * 不依赖 VariableRegistry（P2-4），用于前端编辑器的"已使用变量"提示。
     *
     * @param expression 表达式
     * @return 变量名列表（去重，保留出现顺序）
     */
    private List<String> extractVariables(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        Matcher m = Pattern
                .compile("\\b([a-zA-Z_]\\w*)\\b").matcher(expression);
        LinkedHashSet<String> vars = new LinkedHashSet<>();
        while (m.find()) {
            String word = m.group(1);
            if (AVIATOR_KEYWORDS.contains(word)) continue;
            if (word.matches("\\d+")) continue;
            // 保留首字母小写或含下划线的标识符（驼峰/蛇形变量名）
            if (Character.isLowerCase(word.charAt(0)) || word.contains("_")) {
                vars.add(word);
            }
        }
        return new ArrayList<>(vars);
    }

    /**
     * 编译表达式（带缓存）
     *
     * @param expression 表达式文本
     * @return 编译后的 Expression
     */
    private Expression compile(String expression) {
        return cache.computeIfAbsent(expression, key -> {
            try {
                return instance.compile(key, true);
            } catch (Exception e) {
                throw new IllegalArgumentException("表达式编译失败: " + key + " (" + e.getMessage() + ")", e);
            }
        });
    }

    /**
     * 清除编译缓存
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 获取缓存大小
     *
     * @return 缓存的表达式数量
     */
    public int cacheSize() {
        return cache.size();
    }

    /**
     * 是否启用沙箱
     *
     * @return 沙箱是否启用
     * @since 1.3.0
     */
    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    /**
     * 带追踪的布尔表达式求值（P1-4 表达式级追踪/归因）
     *
     * <p>利用 Aviator AST 解析能力，将表达式拆解为子表达式并逐步求值，
     * 构建表达式执行追踪树，用于归因分析和可视化。
     *
     * <p>追踪粒度：
     * <ul>
     *   <li>逻辑运算符（&&/||）：拆解左右操作数，记录短路信息</li>
     *   <li>比较运算符（&gt;/&lt;/&gt;=/&lt;=/==/!=）：记录变量值和比较结果</li>
     *   <li>变量引用：记录变量名和实际值</li>
     *   <li>字面值：记录字面值</li>
     * </ul>
     */
    @Override
    public TraceResult evalBooleanWithTrace(String expression, RuleContext context) {
        if (expression == null || expression.isBlank()) {
            ExpressionTraceNode root = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .expression(expression)
                    .result(false)
                    .error("表达式为空")
                    .build();
            return new TraceResult(false, root);
        }
        long start = System.nanoTime();
        try {
            sandboxCheck(expression);
            boolean result = evalBoolean(expression, context);
            long elapsed = System.nanoTime() - start;
            // 构建追踪树
            ExpressionTraceNode traceTree = buildTraceTree(expression, context, result, elapsed);
            return new TraceResult(result, traceTree);
        } catch (SecurityException e) {
            long elapsed = System.nanoTime() - start;
            ExpressionTraceNode root = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .expression(expression)
                    .result(false)
                    .error("沙箱拦截: " + e.getMessage())
                    .elapsedNanos(elapsed)
                    .build();
            return new TraceResult(false, root);
        } catch (Exception e) {
            long elapsed = System.nanoTime() - start;
            ExpressionTraceNode root = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .expression(expression)
                    .result(false)
                    .error("求值异常: " + e.getMessage())
                    .elapsedNanos(elapsed)
                    .build();
            return new TraceResult(false, root);
        }
    }

    /** 逻辑运算符正则（按优先级：|| 最低，&& 次低） */
    private static final Pattern OR_PATTERN = Pattern.compile("\\s\\|\\|\\s");
    private static final Pattern AND_PATTERN = Pattern.compile("\\s&&\\s");
    /** 比较运算符正则 */
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "^([a-zA-Z_]\\w*)\\s*(>=|<=|>|<|==|!=)\\s*(.+)$");

    /**
     * 构建表达式追踪树
     *
     * <p>采用正则拆解 + 递归构建方式，支持 AND/OR 短路追踪。
     *
     * @param expression 表达式
     * @param context    规则上下文
     * @param finalResult 最终求值结果
     * @param elapsedNanos 执行耗时
     * @return 追踪树根节点
     */
    private ExpressionTraceNode buildTraceTree(String expression, RuleContext context,
                                                 boolean finalResult, long elapsedNanos) {
        String trimmed = expression.trim();

        // 1. 尝试拆解 OR (||)
        Matcher orMatcher = OR_PATTERN.matcher(trimmed);
        if (orMatcher.find()) {
            return buildLogicalTrace("||", trimmed, context, finalResult, elapsedNanos, orMatcher);
        }

        // 2. 尝试拆解 AND (&&)
        Matcher andMatcher = AND_PATTERN.matcher(trimmed);
        if (andMatcher.find()) {
            return buildLogicalTrace("&&", trimmed, context, finalResult, elapsedNanos, andMatcher);
        }

        // 3. 尝试解析比较表达式
        Matcher compMatcher = COMPARISON_PATTERN.matcher(trimmed);
        if (compMatcher.matches()) {
            String varName = compMatcher.group(1);
            String operator = compMatcher.group(2);
            String rightExpr = compMatcher.group(3).trim();
            Object varValue = context.getFacts().get(varName);
            Object rightValue = parseLiteral(rightExpr, context);
            boolean compResult = finalResult; // 单个比较表达式结果就是最终结果
            return ExpressionTraceNode.comparison(operator, varName, varValue, rightExpr, rightValue, compResult);
        }

        // 4. 单个变量或字面值
        Object value = context.getFacts().get(trimmed);
        if (value != null) {
            return ExpressionTraceNode.variable(trimmed, value);
        }

        // 5. 兜底：根节点
        return ExpressionTraceNode.builder()
                .nodeType(ExpressionTraceNode.NodeType.ROOT)
                .expression(expression)
                .result(finalResult)
                .elapsedNanos(elapsedNanos)
                .build();
    }

    /**
     * 构建逻辑运算追踪节点
     */
    private ExpressionTraceNode buildLogicalTrace(String operator, String expression,
                                                    RuleContext context, boolean finalResult,
                                                    long elapsedNanos, Matcher matcher) {
        String left = expression.substring(0, matcher.start()).trim();
        String right = expression.substring(matcher.end()).trim();
        // 分别求值左右表达式
        boolean leftResult = evalBoolean(left, context);
        boolean shortCircuited = false;
        boolean rightResult = false;
        // 短路分析
        if ("&&".equals(operator) && !leftResult) {
            // AND 短路：左侧 false，右侧不执行
            shortCircuited = true;
        } else if ("||".equals(operator) && leftResult) {
            // OR 短路：左侧 true，右侧不执行
            shortCircuited = true;
        } else {
            rightResult = evalBoolean(right, context);
        }

        ExpressionTraceNode leftNode = buildTraceTree(left, context, leftResult, 0);
        ExpressionTraceNode rightNode = shortCircuited
                ? ExpressionTraceNode.builder()
                        .nodeType(ExpressionTraceNode.NodeType.ROOT)
                        .expression(right)
                        .shortCircuited(true)
                        .error("短路跳过")
                        .build()
                : buildTraceTree(right, context, rightResult, 0);

        return ExpressionTraceNode.builder()
                .nodeType(ExpressionTraceNode.NodeType.LOGICAL)
                .operator(operator)
                .expression(expression)
                .result(finalResult)
                .shortCircuited(shortCircuited)
                .elapsedNanos(elapsedNanos)
                .children(List.of(leftNode, rightNode))
                .build();
    }

    /**
     * 解析字面值（从表达式或上下文中获取）
     */
    private Object parseLiteral(String expr, RuleContext context) {
        if (expr == null || expr.isBlank()) return null;
        // 尝试从上下文中获取变量值
        Object ctxValue = context.getFacts().get(expr);
        if (ctxValue != null) return ctxValue;
        // 尝试解析数字
        try {
            if (expr.contains(".")) {
                return Double.parseDouble(expr);
            } else {
                return Integer.parseInt(expr);
            }
        } catch (NumberFormatException e) {
            // 尝试解析布尔值
            if ("true".equalsIgnoreCase(expr)) return true;
            if ("false".equalsIgnoreCase(expr)) return false;
            return expr; // 字符串字面值
        }
    }
}
