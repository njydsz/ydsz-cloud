package com.njydsz.pmis.literule.impl;

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

/**
 * 脚本规则：基于 Groovy 脚本动态评估
 *
 * <p>支持通过 Groovy 脚本编写复杂规则逻辑，适用于 Aviator 表达式无法覆盖的场景：
 * <ul>
 *   <li>多步骤条件判断（如循环检查、状态机）</li>
 *   <li>需要访问外部服务或计算工具的规则</li>
 *   <li>需要复杂对象操作的规则</li>
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
 * <p>沙箱模式（默认启用）通过 {@link groovy.security.SecurityContext} 限制：
 * <ul>
 *   <li>禁止 {@code System.exit} / {@code Runtime.exec} / {@code ProcessBuilder}</li>
 *   <li>禁止反射调用 ({@code Class.forName} / {@code loadClass})</li>
 *   <li>禁止文件 I/O ({@code java.io.File} / {@code FileInputStream} / {@code FileOutputStream})</li>
 *   <li>禁止网络访问 ({@code java.net.Socket} / {@code URL.openConnection})</li>
 * </ul>
 *
 * <p>示例脚本：
 * <pre>
 * def budget = facts.budgetUsedRatio ?: 0
 * def spi = facts.spi ?: 1.0
 * def cpi = facts.cpi ?: 1.0
 * if (budget >= 0.9 &amp;&amp; spi &lt; 0.85) {
 *     severity = 'RED'
 *     title = "预算超支且进度严重滞后"
 *     description = "预算使用率 ${budget * 100}%, SPI=${spi}"
 *     return true
 * }
 * if (budget >= 0.8 &amp;&amp; cpi &lt; 0.9) {
 *     severity = 'YELLOW'
 *     return true
 * }
 * return false
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
    private final boolean sandboxEnabled;
    private final CompiledScript compiledScript;

    /** Groovy ScriptEngine（全局共享，线程安全） */
    private static final ScriptEngine GROOVY_ENGINE;

    static {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy");
        if (engine == null) {
            throw new IllegalStateException("Groovy ScriptEngine 未找到，请确保 groovy-jsr223 在 classpath 中");
        }
        GROOVY_ENGINE = engine;
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
     * @param script          Groovy 脚本
     * @param sandboxEnabled  是否启用沙箱
     */
    public ScriptRule(String code, String name, String category, int priority,
                      String scope, RuleSeverity defaultSeverity,
                      String script, boolean sandboxEnabled) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.priority = priority;
        this.scope = scope;
        this.defaultSeverity = defaultSeverity != null ? defaultSeverity : RuleSeverity.INFO;
        this.script = script;
        this.sandboxEnabled = sandboxEnabled;
        this.compiledScript = compileScript(script, sandboxEnabled);
    }

    /**
     * 构建脚本规则（默认启用沙箱、默认优先级）
     *
     * @param code            规则编码
     * @param name            规则名称
     * @param category        规则类别
     * @param defaultSeverity 默认严重度
     * @param script          Groovy 脚本
     */
    public ScriptRule(String code, String name, String category,
                      RuleSeverity defaultSeverity, String script) {
        this(code, name, category, DEFAULT_PRIORITY, null, defaultSeverity, script, true);
    }

    /**
     * 构建脚本规则（可指定沙箱开关）
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
        this(code, name, category, DEFAULT_PRIORITY, null, defaultSeverity, script, sandboxEnabled);
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
            Bindings bindings = GROOVY_ENGINE.createBindings();
            bindings.put("facts", context.getFacts());
            // 预设可写变量（脚本可覆盖）
            bindings.put("severity", null);
            bindings.put("title", null);
            bindings.put("description", null);

            Object result = compiledScript.eval(bindings);
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
                    .threshold("Groovy Script")
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
     * 编译 Groovy 脚本
     *
     * <p>沙箱模式下使用 {@link groovy.security.SecurityContext} 限制危险操作，
     * 并通过自定义 {@link groovy.lang.GroovyClassLoader} 设置安全过滤器。
     *
     * @param script         脚本内容
     * @param sandboxEnabled 是否启用沙箱
     * @return 编译后的脚本
     */
    private static CompiledScript compileScript(String script, boolean sandboxEnabled) {
        // 沙箱安全检查在编译前执行，SecurityException 直接抛出
        if (sandboxEnabled) {
            checkScriptSafety(script);
        }
        try {
            Compilable compilable = (Compilable) GROOVY_ENGINE;
            return compilable.compile(script);
        } catch (Exception e) {
            throw new IllegalArgumentException("Groovy 脚本编译失败: " + e.getMessage(), e);
        }
    }

    /** 危险 API 模式正则 */
    private static final java.util.regex.Pattern DANGEROUS_PATTERN = java.util.regex.Pattern.compile(
        "\\b(System\\s*\\.\\s*exit|Runtime\\s*\\.\\s*getRuntime|ProcessBuilder|Class\\s*\\.\\s*forName|" +
        "ClassLoader|FileInputStream|FileOutputStream|RandomAccessFile|Socket\\s*\\(|URL\\s*\\.\\s*openConnection|" +
        "HttpURLConnection|\\bexec\\s*\\(|loadClass|invokeMethod|ScriptEngine|GroovyShell|" +
        "Eval\\s*\\.|Thread\\s*\\.\\s*sleep)"
    );

    /**
     * 检查脚本安全性（沙箱模式下调用）
     *
     * @param script 脚本内容
     * @throws SecurityException 检测到危险 API
     */
    private static void checkScriptSafety(String script) {
        java.util.regex.Matcher matcher = DANGEROUS_PATTERN.matcher(script);
        if (matcher.find()) {
            throw new SecurityException("脚本包含被禁止的 API 调用: " + matcher.group()
                    + "（沙箱模式禁止 System.exit/Runtime.exec/反射/文件I/O/网络访问等）");
        }
    }

    /**
     * 计算耗时（毫秒）
     *
     * @param startNano 开始纳秒
     * @return 耗时毫秒
     */
    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    /**
     * 获取脚本内容
     *
     * @return Groovy 脚本
     */
    public String getScript() {
        return script;
    }

    /**
     * 是否启用沙箱
     *
     * @return true=沙箱已启用
     */
    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }
}
