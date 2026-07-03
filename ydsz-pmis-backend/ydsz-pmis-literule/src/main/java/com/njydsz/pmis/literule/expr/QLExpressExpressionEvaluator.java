package com.njydsz.pmis.literule.expr;

import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.ql.util.express.IExpressContext;
import com.njydsz.pmis.literule.api.RuleContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * QLExpress 表达式评估器（P0-3 真正实现）
 *
 * <p>对标 AviatorExpressionEvaluator，提供等价的：
 * <ul>
 *   <li>编译缓存（按表达式 source 缓存 ExpressRunner，避免重复解析）</li>
 *   <li>沙箱执行（不暴露危险 API）</li>
 *   <li>详细校验（结构校验 + 详细错误信息 + 错误行号列号）</li>
 * </ul>
 *
 * <p>典型用法（application.yml）：
 * <pre>
 *   pmis.literule.evaluator=qlexpress
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class QLExpressExpressionEvaluator implements ExpressionEvaluator {

    /**
     * 表达式编译缓存：source → ExpressRunner 实例
     */
    private final Map<String, ExpressRunner> compileCache = new ConcurrentHashMap<>(256);

    /**
     * 危险模式（沙箱拦截）
     *
     * <p>对齐 Aviator 沙箱策略：
     * <ul>
     *   <li>System.exit / System.getProperties / System.getenv</li>
     *   <li>Runtime.getRuntime / ProcessBuilder</li>
     *   <li>Class.forName / ClassLoader</li>
     *   <li>反射 API：Method/Field/Constructor.invoke</li>
     *   <li>文件 / 网络 IO</li>
     *   <li>脚本引擎</li>
     * </ul>
     */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "(?i)("
            + "System\\.(exit|getProperties|getenv|setProperty)"
            + "|Runtime\\.getRuntime"
            + "|ProcessBuilder"
            + "|Class\\.forName"
            + "|ClassLoader"
            + "|Method\\.invoke|Field\\.set|Constructor\\.newInstance"
            + "|FileInputStream|FileOutputStream|Files\\.(read|write|delete|create)"
            + "|java\\.io\\.|java\\.net\\."
            + "|java\\.lang\\.reflect\\."
            + "|ScriptEngine"
            + "|\\bexec\\b"
            + ")"
    );

    /** QLExpress 关键字（用于 validateDetailed 排除） */
    private static final java.util.Set<String> QL_KEYWORDS = java.util.Set.of(
            "true", "false", "nil", "null",
            "if", "else", "for", "while", "break", "continue", "return",
            "and", "or", "not", "in", "instanceof",
            "concat", "length", "upper", "lower", "contains",
            "startsWith", "endsWith", "isNull", "isNotNull",
            "toNumber", "toString", "dateFormat", "now"
    );

    public QLExpressExpressionEvaluator() {
        log.info("[QLExpress] 表达式评估器初始化完成");
    }

    @Override
    public boolean evalBoolean(String expression, RuleContext context) {
        Object result = eval(expression, context);
        if (result instanceof Boolean b) return b;
        if (result instanceof Number n) return n.doubleValue() != 0;
        return Boolean.parseBoolean(String.valueOf(result));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T eval(String expression, RuleContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        try {
            sandboxCheck(expression);
            ExpressRunner runner = compileCache.computeIfAbsent(expression, this::createRunner);
            IExpressContext<String, Object> ctx = new DefaultContext<>();
            if (context != null && context.getFacts() != null) {
                context.getFacts().forEach(ctx::put);
            }
            return (T) runner.execute(expression, ctx, Collections.emptyList(), true, false);
        } catch (SecurityException e) {
            log.warn("[QLExpress] 安全拦截: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[QLExpress] 表达式求值失败: expr='{}', error={}", expression, e.getMessage());
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
            ExpressRunner runner = new ExpressRunner(true, false);
            runner.parseInstruction(expression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ExpressionValidationResult validateDetailed(String expression) {
        long start = System.nanoTime();

        // 1. 空表达式
        if (expression == null || expression.isBlank()) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            return ExpressionValidationResult.fail(expression,
                    ExpressionValidationResult.ErrorType.EMPTY,
                    "表达式为空", elapsed);
        }

        // 2. 沙箱拦截
        try {
            sandboxCheck(expression);
        } catch (SecurityException e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            return ExpressionValidationResult.fail(expression,
                    ExpressionValidationResult.ErrorType.SANDBOX_VIOLATION,
                    e.getMessage(), elapsed);
        }

        // 3. 编译校验
        try {
            ExpressRunner runner = new ExpressRunner(true, false);
            runner.parseInstruction(expression);
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000L;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            int line = -1;
            int column = -1;
            // 尝试从 QLExpress 错误消息中解析行列号（QLExpress 消息格式包含 [line,column]）
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\[(\\d+),\\s*(\\d+)\\]").matcher(msg);
            if (m.find()) {
                line = Integer.parseInt(m.group(1));
                column = Integer.parseInt(m.group(2));
            }
            return ExpressionValidationResult.builder()
                    .valid(false)
                    .errorType(ExpressionValidationResult.ErrorType.SYNTAX_ERROR)
                    .errorMessage(msg)
                    .errorLine(line)
                    .errorColumn(column)
                    .expression(expression)
                    .parseTimeMs(elapsed)
                    .referencedVariables(new java.util.ArrayList<>())
                    .build();
        }

        // 4. 校验通过，提取引用变量
        long elapsed = (System.nanoTime() - start) / 1_000_000L;
        List<String> vars = extractVariables(expression);
        return ExpressionValidationResult.ok(expression, elapsed, vars);
    }

    /**
     * 沙箱安全校验
     */
    private void sandboxCheck(String expression) {
        if (DANGEROUS_PATTERN.matcher(expression).find()) {
            throw new SecurityException("表达式包含危险操作，已被沙箱拦截: " + expression);
        }
    }

    /**
     * 编译表达式为可执行 Runner
     */
    private ExpressRunner createRunner(String expr) {
        if (compileCache.size() >= 1024) {
            log.warn("[QLExpress] 编译缓存超过 1024 条, 建议评估是否需要清理");
        }
        return new ExpressRunner(true, false);
    }

    /**
     * 清空编译缓存（用于热加载后强制重编）
     */
    public void clearCache() {
        compileCache.clear();
    }

    /**
     * 当前缓存数量
     */
    public int cacheSize() {
        return compileCache.size();
    }

    /**
     * 从表达式中提取引用的变量名
     */
    private List<String> extractVariables(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b([a-zA-Z_]\\w*)\\b").matcher(expression);
        java.util.LinkedHashSet<String> vars = new java.util.LinkedHashSet<>();
        while (m.find()) {
            String word = m.group(1);
            if (QL_KEYWORDS.contains(word)) continue;
            if (word.matches("\\d+")) continue;
            if (Character.isLowerCase(word.charAt(0)) || word.contains("_")) {
                vars.add(word);
            }
        }
        return new java.util.ArrayList<>(vars);
    }
}
