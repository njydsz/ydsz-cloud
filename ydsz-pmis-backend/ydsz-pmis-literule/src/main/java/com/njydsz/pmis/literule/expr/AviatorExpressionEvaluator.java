package com.njydsz.pmis.literule.expr;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.njydsz.pmis.literule.api.RuleContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
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
     * 危险表达式模式（沙箱模式下阻断）
     *
     * <p>阻断以下危险模式：
     * <ul>
     *   <li>System.exit / System.getProperties / System.getenv</li>
     *   <li>Runtime.getRuntime / ProcessBuilder</li>
     *   <li>Class.forName / ClassLoader</li>
     *   <li>Thread / Process</li>
     *   <li>反射 API：Method/Field/Constructor.invoke</li>
     *   <li>文件 I/O：FileInputStream/FileOutputStream/Files</li>
     *   <li>网络 I/O：Socket/URL/HttpURLConnection</li>
     *   <li>脚本引擎：ScriptEngine</li>
     * </ul>
     */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            // 类名以完整或部分方式出现即阻断
            "(?i)("
            + "System\\.(exit|getProperties|getenv|setProperty)"  // 系统操作
            + "|Runtime\\.getRuntime"                              // 运行时执行
            + "|ProcessBuilder"                                    // 进程创建
            + "|Class\\.forName"                                   // 反射加载类
            + "|ClassLoader"                                       // 类加载器
            + "|\\bThread\\.(sleep|interrupt|stop|destroy)"        // 线程控制
            + "|Method\\.invoke|Field\\.set|Constructor\\.newInstance" // 反射调用
            + "|FileInputStream|FileOutputStream|Files\\.(read|write|delete|create)" // 文件 I/O
            + "|java\\.io\\.|java\\.net\\."                         // IO 和网络包
            + "|java\\.lang\\.reflect\\."                           // 反射包
            + "|ScriptEngine"                                      // 脚本引擎
            + "|\\bexec\\b"                                        // 命令执行
            + ")"
    );

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
     * <p>在沙箱模式下，检查表达式是否包含危险模式。
     * 若包含危险模式，抛出 {@link SecurityException}。
     *
     * @param expression 表达式文本
     * @throws SecurityException 表达式包含危险操作
     */
    private void sandboxCheck(String expression) {
        if (!sandboxEnabled) {
            return;
        }
        if (DANGEROUS_PATTERN.matcher(expression).find()) {
            throw new SecurityException("表达式包含危险操作，已被沙箱拦截: " + expression);
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
}
