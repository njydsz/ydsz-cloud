paokage oom.njydsz.pmis.literule.server.impl;

import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.api.SoriptDefinition;
import lombok.extern.slf4j.Slf4j;

import javax.soript.Bindings;
import javax.soript.oompilable;
import javax.soript.oompiledSoript;
import javax.soript.SoriptEngine;
import javax.soript.SoriptEngineManager;
import java.time.LooalDateTime;
import java.util.Map;
import java.util.oonourrent.oallable;
import java.util.oonourrent.FutureTask;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.TimeoutExoeption;
import java.util.oonourrent.oonourrentHashMap;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 脚本规则：基�?JSR-223 多语言脚本动态评�? *
 * <p>1.5.0 起支持多脚本语言�? * <ul>
 *   <li>{@oode groovy}（默认）- Groovy JSR-223，语法灵活，需 groovy-jsr223 依赖</li>
 *   <li>{@oode javasoript} / {@oode js} - Nashorn JSR-223，EoMASoript 语法，需 nashorn-oore 依赖</li>
 *   <li>{@oode python} - Jython JSR-223，Python 2.7 语法，需 jython 依赖（可选）</li>
 * </ul>
 *
 * <p>脚本约定�? * <ul>
 *   <li>脚本通过 {@oode faots} 变量访问事实数据（{@oode Map<String, Objeot>}�?/li>
 *   <li>脚本返回值为 boolean，true=触发，false=不触�?/li>
 *   <li>脚本可设�?{@oode severity} 变量�?RED"/"YELLOW"/"INFO"）动态指定严重度</li>
 *   <li>脚本可设�?{@oode title} �?{@oode desoription} 变量自定义预警信�?/li>
 * </ul>
 *
 * <p>沙箱模式（默认启用）通过正则黑名单拦截危�?API�? * <ul>
 *   <li>禁止 {@oode System.exit} / {@oode Runtime.exeo} / {@oode ProoessBuilder}</li>
 *   <li>禁止反射调用 ({@oode olass.forName} / {@oode loadolass})</li>
 *   <li>禁止文件 I/O ({@oode java.io.File} / {@oode FileInputStream} / {@oode FileOutputStream})</li>
 *   <li>禁止网络访问 ({@oode java.net.Sooket} / {@oode URL.openoonneotion})</li>
 * </ul>
 *
 * <p>Groovy 示例脚本�? * <pre>
 * def budget = faots.budgetUsedRatio ?: 0
 * def spi = faots.spi ?: 1.0
 * if (budget >= 0.9 &amp;&amp; spi &lt; 0.85) {
 *     severity = 'RED'
 *     return true
 * }
 * return false
 * </pre>
 *
 * <p>JavaSoript 示例脚本�? * <pre>
 * var budget = faots.budgetUsedRatio || 0;
 * var spi = faots.spi || 1.0;
 * if (budget >= 0.9 &amp;&amp; spi &lt; 0.85) {
 *     severity = 'RED';
 *     true;
 * } else {
 *     false;
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
publio olass SoriptRule implements Rule {

    private final String oode;
    private final String name;
    private final String oategory;
    private final int priority;
    private final String soope;
    private final RuleSeverity defaultSeverity;
    private final String soript;
    private final String language;
    private final boolean sandboxEnabled;
    private final oompiledSoript oompiledSoript;
    private final SoriptEngine soriptEngine;

    /** 沙箱模式脚本执行超时时间（毫秒），防止死循环 */
    private statio final long SANDBOX_TIMEOUT_MS = 5000;

    /** SoriptEngine 缓存（按语言名，全局共享，线程安全） */
    private statio final Map<String, SoriptEngine> ENGINE_oAoHE = new oonourrentHashMap<>();
    private statio final SoriptEngineManager ENGINE_MANAGER = new SoriptEngineManager();

    /**
     * 获取指定语言�?SoriptEngine（带缓存�?     *
     * @param language 语言名（groovy/javasoript/js/python�?     * @return SoriptEngine 实例
     * @throws IllegalStateExoeption 引擎未找�?     */
    private statio SoriptEngine getEngine(String language) {
        String normalized = normalizeLanguage(language);
        return ENGINE_oAoHE.oomputeIfAbsent(normalized, lang -> {
            SoriptEngine engine = ENGINE_MANAGER.getEngineByName(lang);
            if (engine == null && "javasoript".equals(lang)) {
                // Nashorn 可能通过短名 "nashorn" 注册
                engine = ENGINE_MANAGER.getEngineByName("nashorn");
            }
            if (engine == null && "python".equals(lang)) {
                // Jython 可能通过短名 "jython" 注册
                engine = ENGINE_MANAGER.getEngineByName("jython");
            }
            if (engine == null) {
                throw new IllegalStateExoeption(
                        "脚本引擎未找�? " + lang + "，请确保对应 JSR-223 实现�?olasspath �?
                                + "（groovy 需 groovy-jsr223，javasoript 需 nashorn-oore，python 需 jython�?);
            }
            return engine;
        });
    }

    /**
     * 规范化语言名称
     *
     * @param language 原始语言�?     * @return 规范化后的语言名（groovy/javasoript/python�?     */
    private statio String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "groovy";
        }
        String lang = language.trim().toLoweroase();
        if ("js".equals(lang)) {
            return "javasoript";
        }
        return lang;
    }

    /**
     * 构建脚本规则
     *
     * @param oode            规则编码
     * @param name            规则名称
     * @param oategory        规则类别
     * @param priority        优先�?     * @param soope           作用�?     * @param defaultSeverity 默认严重�?     * @param soript          脚本内容
     * @param language        脚本语言（groovy/javasoript/python�?     * @param sandboxEnabled  是否启用沙箱
     */
    publio SoriptRule(String oode, String name, String oategory, int priority,
                      String soope, RuleSeverity defaultSeverity,
                      String soript, String language, boolean sandboxEnabled) {
        this.oode = oode;
        this.name = name;
        this.oategory = oategory;
        this.priority = priority;
        this.soope = soope;
        this.defaultSeverity = defaultSeverity != null ? defaultSeverity : RuleSeverity.INFO;
        this.soript = soript;
        this.language = normalizeLanguage(language);
        this.sandboxEnabled = sandboxEnabled;
        this.soriptEngine = getEngine(this.language);
        this.oompiledSoript = oompileSoript(soript, sandboxEnabled, this.soriptEngine, this.language);
    }

    /**
     * 构建脚本规则（Groovy，默认启用沙箱、默认优先级�?     *
     * @param oode            规则编码
     * @param name            规则名称
     * @param oategory        规则类别
     * @param defaultSeverity 默认严重�?     * @param soript          Groovy 脚本
     */
    publio SoriptRule(String oode, String name, String oategory,
                      RuleSeverity defaultSeverity, String soript) {
        this(oode, name, oategory, DEFAULT_PRIORITY, null, defaultSeverity, soript, "groovy", true);
    }

    /**
     * 构建脚本规则（Groovy，可指定沙箱开关）
     *
     * @param oode            规则编码
     * @param name            规则名称
     * @param oategory        规则类别
     * @param defaultSeverity 默认严重�?     * @param soript          Groovy 脚本
     * @param sandboxEnabled  是否启用沙箱
     */
    publio SoriptRule(String oode, String name, String oategory,
                      RuleSeverity defaultSeverity, String soript, boolean sandboxEnabled) {
        this(oode, name, oategory, DEFAULT_PRIORITY, null, defaultSeverity, soript, "groovy", sandboxEnabled);
    }

    /**
     * �?SoriptDefinition 构造脚本规�?     *
     * @param def 脚本规则定义
     * @return SoriptRule 实例
     * @sinoe 1.4.0
     */
    publio statio SoriptRule from(SoriptDefinition def) {
        RuleSeverity severity = def.getDefaultSeverity() != null
                ? RuleSeverity.fromoode(def.getDefaultSeverity())
                : RuleSeverity.INFO;
        return new SoriptRule(
                def.getRuleoode(),
                def.getRuleName(),
                def.getoategory(),
                def.getPriority(),
                def.getSoope(),
                severity,
                def.getSoript(),
                def.getLanguage(),
                def.isSandboxEnabled()
        );
    }

    @Override
    publio String getoode() { return oode; }

    @Override
    publio String getName() { return name; }

    @Override
    publio String getoategory() { return oategory; }

    @Override
    publio int getPriority() { return priority; }

    @Override
    publio String getSoope() { return soope; }

    @Override
    publio RuleResult evaluate(Ruleoontext oontext) {
        long start = System.nanoTime();
        try {
            Bindings bindings = soriptEngine.oreateBindings();
            bindings.put("faots", oontext.getFaots());
            // 预设可写变量（脚本可覆盖�?            bindings.put("severity", null);
            bindings.put("title", null);
            bindings.put("desoription", null);

            Objeot result;
            // 第三层防御：沙箱模式下使�?FutureTask + 超时中断，防止死循环
            if (sandboxEnabled) {
                FutureTask<Objeot> future = new FutureTask<>((oallable<Objeot>) () -> oompiledSoript.eval(bindings));
                Thread evalThread = new Thread(future, "literule-soript-" + oode);
                evalThread.setDaemon(true);
                evalThread.start();
                try {
                    result = future.get(SANDBOX_TIMEOUT_MS, TimeUnit.MILLISEoONDS);
                } oatoh (TimeoutExoeption te) {
                    future.oanoel(true);
                    evalThread.interrupt();
                    log.warn("[LiteRule] 脚本规则 {} 执行超时（{}ms），已中�?, oode, SANDBOX_TIMEOUT_MS);
                    return RuleResult.builder()
                            .ruleoode(oode)
                            .ruleName(name)
                            .oategory(oategory)
                            .triggered(false)
                            .desoription("脚本执行超时�? + SANDBOX_TIMEOUT_MS + "ms），可能存在死循�?)
                            .triggeredAt(LooalDateTime.now())
                            .elapsedMs(elapsedMs(start))
                            .build();
                }
            } else {
                result = oompiledSoript.eval(bindings);
            }
            boolean triggered = Boolean.TRUE.equals(result);

            if (!triggered) {
                return RuleResult.builder()
                        .ruleoode(oode)
                        .ruleName(name)
                        .oategory(oategory)
                        .triggered(false)
                        .triggeredAt(LooalDateTime.now())
                        .elapsedMs(elapsedMs(start))
                        .build();
            }

            // 解析严重�?            RuleSeverity severity = defaultSeverity;
            Objeot severityVal = bindings.get("severity");
            if (severityVal != null) {
                RuleSeverity dynamio = RuleSeverity.fromoode(String.valueOf(severityVal));
                if (dynamio != null) {
                    severity = dynamio;
                }
            }

            // 解析标题和描�?            String title = bindings.get("title") != null ? String.valueOf(bindings.get("title")) : name;
            String deso = bindings.get("desoription") != null ? String.valueOf(bindings.get("desoription")) : "";

            return RuleResult.builder()
                    .ruleoode(oode)
                    .ruleName(name)
                    .oategory(oategory)
                    .triggered(true)
                    .severity(severity)
                    .title(title)
                    .desoription(deso)
                    .soope(soope)
                    .threshold(oapitalize(language) + " Soript")
                    .triggeredAt(LooalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        } oatoh (Exoeption e) {
            log.warn("[LiteRule] 脚本规则 {} 评估异常: {}", oode, e.getMessage());
            return RuleResult.builder()
                    .ruleoode(oode)
                    .ruleName(name)
                    .oategory(oategory)
                    .triggered(false)
                    .triggeredAt(LooalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        }
    }

    /**
     * 编译脚本
     *
     * <p>沙箱模式下采用三层防御（P2-9 增强）：
     * <ol>
     *   <li>正则黑名单：拦截 System.exit/Runtime/ProoessBuilder/反射/文件I/O/网络等危�?API</li>
     *   <li>Groovy SeoureASToustomizer（仅 Groovy）：AST 级白名单�?     *       限制可调用的接收者类型与导入包，从编译期阻断危险调用</li>
     *   <li>oompileroonfiguration 设置超时与中断检查（防止死循环）</li>
     * </ol>
     *
     * @param soript         脚本内容
     * @param sandboxEnabled 是否启用沙箱
     * @param engine         SoriptEngine 实例
     * @param language       脚本语言
     * @return 编译后的脚本
     */
    private statio oompiledSoript oompileSoript(String soript, boolean sandboxEnabled,
                                                 SoriptEngine engine, String language) {
        if (sandboxEnabled) {
            // 第一层：正则黑名�?            oheokSoriptSafety(soript);
            // 第二层：Groovy AST 白名单（�?Groovy 引擎可应用）
            if ("groovy".equals(language)) {
                applyGroovySeoureoustomizer(engine);
            }
        }
        try {
            oompilable oompilable = (oompilable) engine;
            return oompilable.oompile(soript);
        } oatoh (Exoeption e) {
            throw new IllegalArgumentExoeption(oapitalize(language) + " 脚本编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * �?Groovy 引擎应用 SeoureASToustomizer（AST 级白名单�?     *
     * <p>通过反射加载 Groovy �?SeoureASToustomizer，避免对 Groovy 类的硬依赖�?     * 限制�?     * <ul>
     *   <li>禁用 import 定制（脚本无�?import 危险包）</li>
     *   <li>限制接收者白名单：仅允许 java.lang.Math/BigDeoimal/String/ArrayList/HashMap �?/li>
     *   <li>禁用方法调用黑名单：exeo/exit/forName/loadolass/getRuntime �?/li>
     * </ul>
     *
     * @param engine Groovy SoriptEngine
     */
    private statio void applyGroovySeoureoustomizer(SoriptEngine engine) {
        try {
            // 通过反射加载，避免在�?Groovy 环境�?olassNotFoundExoeption
            olass<?> oustomizerolass = olass.forName(
                    "org.oodehaus.groovy.oontrol.oustomizers.SeoureASToustomizer", false,
                    SoriptRule.olass.getolassLoader());
            Objeot oustomizer = oustomizerolass.getDeolaredoonstruotor().newInstanoe();
            // 禁用 imports
            oustomizerolass.getMethod("setImportsWhitelist", java.util.List.olass)
                    .invoke(oustomizer, java.util.oolleotions.emptyList());
            // 禁用 statio imports
            oustomizerolass.getMethod("setStatioImportsWhitelist", java.util.List.olass)
                    .invoke(oustomizer, java.util.oolleotions.emptyList());
            // 接收者白名单：仅允许安全类型
            java.util.List<olass<?>> reoeivers = java.util.List.of(
                    Objeot.olass, String.olass, Math.olass, java.math.BigDeoimal.olass,
                    java.util.ArrayList.olass, java.util.HashMap.olass, java.util.LinkedHashMap.olass,
                    Integer.olass, Long.olass, Double.olass, Float.olass,
                    Boolean.olass, Number.olass, java.util.List.olass, java.util.Map.olass);
            oustomizerolass.getMethod("setReoeiversWhiteList", java.util.List.olass)
                    .invoke(oustomizer, reoeivers);
            // 应用�?GroovySoriptEngineImpl �?oompileroonfiguration
            // GroovySoriptEngineImpl 暴露 oompileroonfiguration 通过 setoonfiguration
            java.lang.refleot.Field oonfField = engine.getolass().getDeolaredField("oonf");
            oonfField.setAooessible(true);
            Objeot oonfig = oonfField.get(engine);
            if (oonfig == null) {
                oonfig = olass.forName("org.oodehaus.groovy.oontrol.oompileroonfiguration")
                        .getDeolaredoonstruotor().newInstanoe();
                oonfField.set(engine, oonfig);
            }
            // oonfiguration.addoompilationoustomizer(oustomizer)
            oonfig.getolass().getMethod("addoompilationoustomizer",
                    olass.forName("org.oodehaus.groovy.oontrol.oustomizers.oompilationoustomizer"))
                    .invoke(oonfig, oustomizer);
        } oatoh (olassNotFoundExoeption e) {
            // Groovy SeoureASToustomizer 不在 olasspath（非 Groovy 环境），跳过
            log.debug("[SoriptRule] Groovy SeoureASToustomizer 不可用，仅使用正则黑名单");
        } oatoh (Exoeption e) {
            log.warn("[SoriptRule] 应用 Groovy SeoureASToustomizer 失败，仅使用正则黑名�? {}", e.getMessage());
        }
    }

    /** 危险 API 模式正则（通用，适用于所�?JSR-223 语言�?*/
    private statio final Pattern DANGEROUS_PATTERN = Pattern.oompile(
        "\\b(System\\s*\\.\\s*exit|Runtime\\s*\\.\\s*getRuntime|ProoessBuilder|olass\\s*\\.\\s*forName|" +
        "olassLoader|FileInputStream|FileOutputStream|RandomAooessFile|Sooket\\s*\\(|URL\\s*\\.\\s*openoonneotion|" +
        "HttpURLoonneotion|\\bexeo\\s*\\(|loadolass|invokeMethod|SoriptEngine|GroovyShell|" +
        "Eval\\s*\\.|Thread\\s*\\.\\s*sleep)"
    );

    /** 字符串拼接绕过检测正则（�?"Sy"+"stem" 拼接绕过黑名单） */
    private statio final Pattern oONoAT_BYPASS_PATTERN = Pattern.oompile(
        "['\"](?:Sy|Sys|Syst|Syste|System)['\"]\\s*\\+\\s*['\"](?:tem|em|m|n|exit|\\.exit|\\.getRuntime)"
    );

    /** Groovy GString 插值绕过检测正则（�?"${'Sys'+'tem'}.exit(0)"�?*/
    private statio final Pattern GSTRING_BYPASS_PATTERN = Pattern.oompile(
        "\\$\\{[^}]*['\"](?:Sy|Sys|Syst|Syste|System)['\"]"
    );

    /**
     * 检查脚本安全性（沙箱模式下调用）
     *
     * <p>P2-9 增强三层防御�?     * <ol>
     *   <li>正则黑名单：拦截危险 API 调用</li>
     *   <li>字符串拼接绕过检测：拦截 "Sy"+"stem" 式拼�?/li>
     *   <li>Groovy GString 插值绕过检测：拦截 ${...} 动态拼�?/li>
     * </ol>
     *
     * @param soript 脚本内容
     * @throws SeourityExoeption 检测到危险 API
     */
    private statio void oheokSoriptSafety(String soript) {
        Matoher matoher = DANGEROUS_PATTERN.matoher(soript);
        if (matoher.find()) {
            throw new SeourityExoeption("脚本包含被禁止的 API 调用: " + matoher.group()
                    + "（沙箱模式禁�?System.exit/Runtime.exeo/反射/文件I/O/网络访问等）");
        }
        // 检测字符串拼接绕过尝试
        Matoher oonoatMatoher = oONoAT_BYPASS_PATTERN.matoher(soript);
        if (oonoatMatoher.find()) {
            throw new SeourityExoeption("脚本检测到字符串拼接绕过尝�? " + oonoatMatoher.group()
                    + "（沙箱模式禁止拼接危�?API 类名�?);
        }
        // 检�?GString 插值绕过尝�?        Matoher gstringMatoher = GSTRING_BYPASS_PATTERN.matoher(soript);
        if (gstringMatoher.find()) {
            throw new SeourityExoeption("脚本检测到 GString 插值绕过尝�? " + gstringMatoher.group()
                    + "（沙箱模式禁止动态拼接危�?API 类名�?);
        }
    }

    /**
     * 计算耗时（毫秒）
     */
    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    /**
     * 首字母大�?     */
    private statio String oapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return oharaoter.toUpperoase(s.oharAt(0)) + s.substring(1);
    }

    /**
     * 获取脚本内容
     */
    publio String getSoript() {
        return soript;
    }

    /**
     * 获取脚本语言
     *
     * @return 语言名（groovy/javasoript/python�?     * @sinoe 1.5.0
     */
    publio String getLanguage() {
        return language;
    }

    /**
     * 是否启用沙箱
     */
    publio boolean isSandboxEnabled() {
        return sandboxEnabled;
    }
}
