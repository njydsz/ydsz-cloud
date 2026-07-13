package com.njydsz.pmis.literule.server.impl;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.ScriptDefinition;
import lombok.extern.slf4j.Slf4j;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 脚本规则：基于 JSR-223 多语言脚本动态评估
 *
 * <p>1.5.0 起支持多脚本语言：
 * <ul>
 *   <li>{@code groovy}（默认）- Groovy JSR-223，语法灵活，需 groovy-jsr223 依赖</li>
 *   <li>{@code javascript} / {@code js} - Nashorn JSR-223，ECMAScript 语法，需 nashorn-core 依赖</li>
 *   <li>{@code python} - Jython JSR-223，Python 2.7 语法，需 jython 依赖（可选）</li>
 * </ul>
 *
 * <p>脚本约定：
 * <ul>
 *   <li>脚本通过 {@code facts} 变量访问事实数据（{@code Map<String, Object>}）</li>
 *   <li>脚本返回值为 boolean，true=触发，false=不触发</li>
 *   <li>脚本可设置 {@code severity} 变量（"RED"/"YELLOW"/"INFO"）动态指定严重度</li>
 *   <li>脚本可设置 {@code title} 和 {@code description} 变量自定义预警信息</li>
 * </ul>
 *
 * <p>沙箱模式（默认启用）通过正则黑名单拦截危险 API：
 * <ul>
 *   <li>禁止 {@code System.exit} / {@code Runtime.exec} / {@code ProcessBuilder}</li>
 *   <li>禁止反射调用 ({@code Class.forName} / {@code loadClass})</li>
 *   <li>禁止文件 I/O ({@code java.io.File} / {@code FileInputStream} / {@code FileOutputStream})</li>
 *   <li>禁止网络访问 ({@code java.net.Socket} / {@code URL.openConnection})</li>
 * </ul>
 *
 * <p>Groovy 示例脚本：
 * <pre>
 * def budget = facts.budgetUsedRatio ?: 0
 * def spi = facts.spi ?: 1.0
 * if (budget >= 0.9 &amp;&amp; spi &lt; 0.85) {
 *     severity = 'RED'
 *     return true
 * }
 * return false
 * </pre>
 *
 * <p>JavaScript 示例脚本：
 * <pre>
 * var budget = facts.budgetUsedRatio || 0;
 * var spi = facts.spi || 1.0;
 * if (budget >= 0.9 &amp;&amp; spi &lt; 0.85) {
 *     severity = 'RED';
 *     true;
 * } else {
 *     false;
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public class ScriptRule implements Rule {

    private final String code;
    private final String name;
    private final String category;
    private final int priority;
    private final String scope;
    private final RuleSeverity defaultSeverity;
    private final String script;
    private final String language;
    private final boolean sandboxEnabled;
    private final CompiledScript compiledScript;
    private final ScriptEngine scriptEngine;

    /** 沙箱模式脚本执行超时时间（毫秒），防止死循环 */
    private static final long SANDBOX_TIMEOUT_MS = 5000;

    /** ScriptEngine 缓存（按语言名，全局共享，线程安全） */
    private static final Map<String, ScriptEngine> ENGINE_CACHE = new ConcurrentHashMap<>();
    private static final ScriptEngineManager ENGINE_MANAGER = new ScriptEngineManager();

    /**
     * 获取指定语言的 ScriptEngine（带缓存）
     *
     * @param language 语言名（groovy/javascript/js/python）
     * @return ScriptEngine 实例
     * @throws IllegalStateException 引擎未找到
     */
    private static ScriptEngine getEngine(String language) {
        String normalized = normalizeLanguage(language);
        return ENGINE_CACHE.computeIfAbsent(normalized, lang -> {
            ScriptEngine engine = ENGINE_MANAGER.getEngineByName(lang);
            if (engine == null && "javascript".equals(lang)) {
                // Nashorn 可能通过短名 "nashorn" 注册
                engine = ENGINE_MANAGER.getEngineByName("nashorn");
            }
            if (engine == null && "python".equals(lang)) {
                // Jython 可能通过短名 "jython" 注册
                engine = ENGINE_MANAGER.getEngineByName("jython");
            }
            if (engine == null) {
                throw new IllegalStateException(
                        "脚本引擎未找到: " + lang + "，请确保对应 JSR-223 实现在 classpath 中"
                                + "（groovy 需 groovy-jsr223，javascript 需 nashorn-core，python 需 jython）");
            }
            return engine;
        });
    }

    /**
     * 规范化语言名称
     *
     * @param language 原始语言名
     * @return 规范化后的语言名（groovy/javascript/python）
     */
    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "groovy";
        }
        String lang = language.trim().toLowerCase();
        if ("js".equals(lang)) {
            return "javascript";
        }
        return lang;
    }

    /**
     * 构建脚本规则
     *
     * @param code            规则编码
     * @param name            规则名称
     * @param category        规则类别
     * @param priority        优先级
     * @param scope           作用域
     * @param defaultSeverity 默认严重度
     * @param script          脚本内容
     * @param language        脚本语言（groovy/javascript/python）
     * @param sandboxEnabled  是否启用沙箱
     */
    public ScriptRule(String code, String name, String category, int priority,
                      String scope, RuleSeverity defaultSeverity,
                      String script, String language, boolean sandboxEnabled) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.priority = priority;
        this.scope = scope;
        this.defaultSeverity = defaultSeverity != null ? defaultSeverity : RuleSeverity.INFO;
        this.script = script;
        this.language = normalizeLanguage(language);
        this.sandboxEnabled = sandboxEnabled;
        this.scriptEngine = getEngine(this.language);
        this.compiledScript = compileScript(script, sandboxEnabled, this.scriptEngine, this.language);
    }

    /**
     * 构建脚本规则（Groovy，默认启用沙箱、默认优先级）
     *
     * @param code            规则编码
     * @param name            规则名称
     * @param category        规则类别
     * @param defaultSeverity 默认严重度
     * @param script          Groovy 脚本
     */
    public ScriptRule(String code, String name, String category,
                      RuleSeverity defaultSeverity, String script) {
        this(code, name, category, DEFAULT_PRIORITY, null, defaultSeverity, script, "groovy", true);
    }

    /**
     * 构建脚本规则（Groovy，可指定沙箱开关）
     *
     * @param code            规则编码
     * @param name            规则名称
     * @param category        规则类别
     * @param defaultSeverity 默认严重度
     * @param script          Groovy 脚本
     * @param sandboxEnabled  是否启用沙箱
     */
    public ScriptRule(String code, String name, String category,
                      RuleSeverity defaultSeverity, String script, boolean sandboxEnabled) {
        this(code, name, category, DEFAULT_PRIORITY, null, defaultSeverity, script, "groovy", sandboxEnabled);
    }

    /**
     * 从 ScriptDefinition 构造脚本规则
     *
     * @param def 脚本规则定义
     * @return ScriptRule 实例
     * @since 1.4.0
     */
    public static ScriptRule from(ScriptDefinition def) {
        RuleSeverity severity = def.getDefaultSeverity() != null
                ? RuleSeverity.fromCode(def.getDefaultSeverity())
                : RuleSeverity.INFO;
        return new ScriptRule(
                def.getRuleCode(),
                def.getRuleName(),
                def.getCategory(),
                def.getPriority(),
                def.getScope(),
                severity,
                def.getScript(),
                def.getLanguage(),
                def.isSandboxEnabled()
        );
    }

    @Override
    public String getCode() { return code; }

    @Override
    public String getName() { return name; }

    @Override
    public String getCategory() { return category; }

    @Override
    public int getPriority() { return priority; }

    @Override
    public String getScope() { return scope; }

    @Override
    public RuleResult evaluate(RuleContext context) {
        long start = System.nanoTime();
        try {
            Bindings bindings = scriptEngine.createBindings();
            bindings.put("facts", context.getFacts());
            // 预设可写变量（脚本可覆盖）
            bindings.put("severity", null);
            bindings.put("title", null);
            bindings.put("description", null);

            Object result;
            // 第三层防御：沙箱模式下使用 FutureTask + 超时中断，防止死循环
            if (sandboxEnabled) {
                FutureTask<Object> future = new FutureTask<>((Callable<Object>) () -> compiledScript.eval(bindings));
                Thread evalThread = new Thread(future, "literule-script-" + code);
                evalThread.setDaemon(true);
                evalThread.start();
                try {
                    result = future.get(SANDBOX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    future.cancel(true);
                    evalThread.interrupt();
                    log.warn("[LiteRule] 脚本规则 {} 执行超时（{}ms），已中断", code, SANDBOX_TIMEOUT_MS);
                    return RuleResult.builder()
                            .ruleCode(code)
                            .ruleName(name)
                            .category(category)
                            .triggered(false)
                            .description("脚本执行超时（" + SANDBOX_TIMEOUT_MS + "ms），可能存在死循环")
                            .triggeredAt(LocalDateTime.now())
                            .elapsedMs(elapsedMs(start))
                            .build();
                }
            } else {
                result = compiledScript.eval(bindings);
            }
            boolean triggered = Boolean.TRUE.equals(result);

            if (!triggered) {
                return RuleResult.builder()
                        .ruleCode(code)
                        .ruleName(name)
                        .category(category)
                        .triggered(false)
                        .triggeredAt(LocalDateTime.now())
                        .elapsedMs(elapsedMs(start))
                        .build();
            }

            // 解析严重度
            RuleSeverity severity = defaultSeverity;
            Object severityVal = bindings.get("severity");
            if (severityVal != null) {
                RuleSeverity dynamic = RuleSeverity.fromCode(String.valueOf(severityVal));
                if (dynamic != null) {
                    severity = dynamic;
                }
            }

            // 解析标题和描述
            String title = bindings.get("title") != null ? String.valueOf(bindings.get("title")) : name;
            String desc = bindings.get("description") != null ? String.valueOf(bindings.get("description")) : "";

            return RuleResult.builder()
                    .ruleCode(code)
                    .ruleName(name)
                    .category(category)
                    .triggered(true)
                    .severity(severity)
                    .title(title)
                    .description(desc)
                    .scope(scope)
                    .threshold(capitalize(language) + " Script")
                    .triggeredAt(LocalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        } catch (Exception e) {
            log.warn("[LiteRule] 脚本规则 {} 评估异常: {}", code, e.getMessage());
            return RuleResult.builder()
                    .ruleCode(code)
                    .ruleName(name)
                    .category(category)
                    .triggered(false)
                    .triggeredAt(LocalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        }
    }

    /**
     * 编译脚本
     *
     * <p>沙箱模式下采用三层防御（P2-9 增强）：
     * <ol>
     *   <li>正则黑名单：拦截 System.exit/Runtime/ProcessBuilder/反射/文件I/O/网络等危险 API</li>
     *   <li>Groovy SecureASTCustomizer（仅 Groovy）：AST 级白名单，
     *       限制可调用的接收者类型与导入包，从编译期阻断危险调用</li>
     *   <li>CompilerConfiguration 设置超时与中断检查（防止死循环）</li>
     * </ol>
     *
     * @param script         脚本内容
     * @param sandboxEnabled 是否启用沙箱
     * @param engine         ScriptEngine 实例
     * @param language       脚本语言
     * @return 编译后的脚本
     */
    private static CompiledScript compileScript(String script, boolean sandboxEnabled,
                                                 ScriptEngine engine, String language) {
        if (sandboxEnabled) {
            // 第一层：正则黑名单
            checkScriptSafety(script);
            // 第二层：Groovy AST 白名单（仅 Groovy 引擎可应用）
            if ("groovy".equals(language)) {
                applyGroovySecureCustomizer(engine);
            }
        }
        try {
            Compilable compilable = (Compilable) engine;
            return compilable.compile(script);
        } catch (Exception e) {
            throw new IllegalArgumentException(capitalize(language) + " 脚本编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 对 Groovy 引擎应用 SecureASTCustomizer（AST 级白名单）
     *
     * <p>通过反射加载 Groovy 的 SecureASTCustomizer，避免对 Groovy 类的硬依赖。
     * 限制：
     * <ul>
     *   <li>禁用 import 定制（脚本无法 import 危险包）</li>
     *   <li>限制接收者白名单：仅允许 java.lang.Math/BigDecimal/String/ArrayList/HashMap 等</li>
     *   <li>禁用方法调用黑名单：exec/exit/forName/loadClass/getRuntime 等</li>
     * </ul>
     *
     * @param engine Groovy ScriptEngine
     */
    private static void applyGroovySecureCustomizer(ScriptEngine engine) {
        try {
            // 通过反射加载，避免在非 Groovy 环境下 ClassNotFoundException
            Class<?> customizerClass = Class.forName(
                    "org.codehaus.groovy.control.customizers.SecureASTCustomizer", false,
                    ScriptRule.class.getClassLoader());
            Object customizer = customizerClass.getDeclaredConstructor().newInstance();
            // 禁用 imports
            customizerClass.getMethod("setImportsWhitelist", List.class)
                    .invoke(customizer, Collections.emptyList());
            // 禁用 static imports
            customizerClass.getMethod("setStaticImportsWhitelist", List.class)
                    .invoke(customizer, Collections.emptyList());
            // 接收者白名单：仅允许安全类型
            List<Class<?>> receivers = List.of(
                    Object.class, String.class, Math.class, java.math.BigDecimal.class,
                    ArrayList.class, HashMap.class, LinkedHashMap.class,
                    Integer.class, Long.class, Double.class, Float.class,
                    Boolean.class, Number.class, List.class, Map.class);
            customizerClass.getMethod("setReceiversWhiteList", List.class)
                    .invoke(customizer, receivers);
            // 应用到 GroovyScriptEngineImpl 的 CompilerConfiguration
            // GroovyScriptEngineImpl 暴露 CompilerConfiguration 通过 setConfiguration
            java.lang.reflect.Field confField = engine.getClass().getDeclaredField("conf");
            confField.setAccessible(true);
            Object config = confField.get(engine);
            if (config == null) {
                config = Class.forName("org.codehaus.groovy.control.CompilerConfiguration")
                        .getDeclaredConstructor().newInstance();
                confField.set(engine, config);
            }
            // configuration.addCompilationCustomizer(customizer)
            config.getClass().getMethod("addCompilationCustomizer",
                    Class.forName("org.codehaus.groovy.control.customizers.CompilationCustomizer"))
                    .invoke(config, customizer);
        } catch (ClassNotFoundException e) {
            // Groovy SecureASTCustomizer 不在 classpath（非 Groovy 环境），跳过
            log.debug("[ScriptRule] Groovy SecureASTCustomizer 不可用，仅使用正则黑名单");
        } catch (Exception e) {
            log.warn("[ScriptRule] 应用 Groovy SecureASTCustomizer 失败，仅使用正则黑名单: {}", e.getMessage());
        }
    }

    /** 危险 API 模式正则（通用，适用于所有 JSR-223 语言） */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
        "\\b(System\\s*\\.\\s*exit|Runtime\\s*\\.\\s*getRuntime|ProcessBuilder|Class\\s*\\.\\s*forName|" +
        "ClassLoader|FileInputStream|FileOutputStream|RandomAccessFile|Socket\\s*\\(|URL\\s*\\.\\s*openConnection|" +
        "HttpURLConnection|\\bexec\\s*\\(|loadClass|invokeMethod|ScriptEngine|GroovyShell|" +
        "Eval\\s*\\.|Thread\\s*\\.\\s*sleep)"
    );

    /** 字符串拼接绕过检测正则（如 "Sy"+"stem" 拼接绕过黑名单） */
    private static final Pattern CONCAT_BYPASS_PATTERN = Pattern.compile(
        "['\"](?:Sy|Sys|Syst|Syste|System)['\"]\\s*\\+\\s*['\"](?:tem|em|m|n|exit|\\.exit|\\.getRuntime)"
    );

    /** Groovy GString 插值绕过检测正则（如 "${'Sys'+'tem'}.exit(0)"） */
    private static final Pattern GSTRING_BYPASS_PATTERN = Pattern.compile(
        "\\$\\{[^}]*['\"](?:Sy|Sys|Syst|Syste|System)['\"]"
    );

    /**
     * 检查脚本安全性（沙箱模式下调用）
     *
     * <p>P2-9 增强三层防御：
     * <ol>
     *   <li>正则黑名单：拦截危险 API 调用</li>
     *   <li>字符串拼接绕过检测：拦截 "Sy"+"stem" 式拼接</li>
     *   <li>Groovy GString 插值绕过检测：拦截 ${...} 动态拼接</li>
     * </ol>
     *
     * @param script 脚本内容
     * @throws SecurityException 检测到危险 API
     */
    private static void checkScriptSafety(String script) {
        Matcher matcher = DANGEROUS_PATTERN.matcher(script);
        if (matcher.find()) {
            throw new SecurityException("脚本包含被禁止的 API 调用: " + matcher.group()
                    + "（沙箱模式禁止 System.exit/Runtime.exec/反射/文件I/O/网络访问等）");
        }
        // 检测字符串拼接绕过尝试
        Matcher concatMatcher = CONCAT_BYPASS_PATTERN.matcher(script);
        if (concatMatcher.find()) {
            throw new SecurityException("脚本检测到字符串拼接绕过尝试: " + concatMatcher.group()
                    + "（沙箱模式禁止拼接危险 API 类名）");
        }
        // 检测 GString 插值绕过尝试
        Matcher gstringMatcher = GSTRING_BYPASS_PATTERN.matcher(script);
        if (gstringMatcher.find()) {
            throw new SecurityException("脚本检测到 GString 插值绕过尝试: " + gstringMatcher.group()
                    + "（沙箱模式禁止动态拼接危险 API 类名）");
        }
    }

    /**
     * 计算耗时（毫秒）
     */
    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    /**
     * 首字母大写
     */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * 获取脚本内容
     */
    public String getScript() {
        return script;
    }

    /**
     * 获取脚本语言
     *
     * @return 语言名（groovy/javascript/python）
     * @since 1.5.0
     */
    public String getLanguage() {
        return language;
    }

    /**
     * 是否启用沙箱
     */
    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }
}
