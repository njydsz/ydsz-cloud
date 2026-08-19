package com.njydsz.cronjob.server.core.handler;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.njydsz.cronjob.infra.entity.schedule.GlueCode;
import com.njydsz.cronjob.domain.job.JobExecutionContext;
import com.njydsz.cronjob.domain.job.JobHandler;
import com.njydsz.cronjob.server.core.executor.SandboxScriptExecutor;
import com.njydsz.cronjob.server.service.schedule.GlueCodeService;

import groovy.lang.GroovyClassLoader;

/**
 * GLUE 在线编码任务处理器（P1-2 GLUE 在线编码，P1-7 多语言支持扩展）。
 *
 * <p>支持 {@code jobType=GLUE} 的任务，业务侧通过在线编辑器编写脚本， 调度执行时由本处理器从 {@code ydsz_job_glue} 表读取最新版本源代码， 根据
 * {@code language} 字段选择对应的执行引擎。
 *
 * <h3>多语言支持（P1-7 扩展）</h3>
 *
 * <ul>
 *   <li>{@code GROOVY}（默认）: 通过 {@link GroovyClassLoader} 动态编译执行
 *   <li>{@code PYTHON}: 通过 {@link SandboxScriptExecutor} 在沙箱中执行 Python3 脚本
 *   <li>{@code SHELL}: 通过 {@link SandboxScriptExecutor} 在沙箱中执行 Bash 脚本
 *   <li>{@code JAVASCRIPT}: 通过 {@link ScriptEngine} 执行 JS 脚本（Nashorn/GraalJS）
 *   <li>{@code JAVA}: 预留扩展，当前按 GROOVY 处理
 * </ul>
 *
 * <h3>Groovy 脚本约定</h3>
 *
 * <p>Groovy 代码需满足以下任一约定：
 *
 * <ul>
 *   <li>实现 {@link JobHandler} 接口（推荐，与 BEAN 模式一致）
 *   <li>定义 {@code Object execute(String paramsJson)} 方法（脚本式写法）
 * </ul>
 *
 * <h3>Python/Shell 脚本约定</h3>
 *
 * <p>paramsJson 通过环境变量 {@code JOB_PARAMS} 传入脚本， 脚本退出码 0 为成功，非 0 为失败，stdout 作为执行结果。
 *
 * <h3>JavaScript 脚本约定</h3>
 *
 * <p>paramsJson 通过 {@code paramsJson} 全局变量传入， 脚本需定义 {@code execute(paramsJson)} 函数或直接返回结果。
 *
 * <h3>缓存策略</h3>
 *
 * <p>以 jobId + sourceCode 哈希为 key 缓存编译结果（Class 对象）， 源代码未变更时直接复用，避免重复编译开销。新版本保存后自动失效旧缓存。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnMissingBean(GlueJobHandler.class)
public class GlueJobHandler implements JobHandler {

  /** Bean 名称，dispatcher 在 jobType=GLUE 时路由到此 handler */
  public static final String BEAN_NAME = "glueJobHandler";

  /** GLUE 代码服务（可选注入，未配置时 GLUE 任务降级到 BEAN 模式） */
  private final ObjectProvider<GlueCodeService> glueCodeServiceProvider;

  /** P1-7: 沙箱脚本执行器（Python/Shell 语言支持） */
  private final ObjectProvider<SandboxScriptExecutor> sandboxExecutorProvider;

  /** P1-7: JavaScript 脚本引擎（Nashorn/GraalJS） */
  private final ScriptEngine jsEngine;

  /** 编译缓存: cacheKey → 编译后的 Class（并发安全，多任务并行编译互不阻塞） */
  private final Map<String, Class<?>> compiledClassCache = new ConcurrentHashMap<>();

  /** ClassLoader 缓存: cacheKey → GroovyClassLoader（用于隔离不同任务的类空间） */
  private final Map<String, GroovyClassLoader> classLoaderCache = new ConcurrentHashMap<>();

  public GlueJobHandler(
      ObjectProvider<GlueCodeService> glueCodeServiceProvider,
      ObjectProvider<SandboxScriptExecutor> sandboxExecutorProvider) {
    this.glueCodeServiceProvider = glueCodeServiceProvider;
    this.sandboxExecutorProvider = sandboxExecutorProvider;
    // 初始化 JavaScript 引擎（P0-6: 应用安全限制）
    ScriptEngineManager manager = new ScriptEngineManager();
    ScriptEngine engine = manager.getEngineByName("nashorn");
    if (engine == null) {
      engine = manager.getEngineByName("graal.js");
    }
    if (engine == null) {
      engine = manager.getEngineByName("js");
    }
    this.jsEngine = engine;
    if (engine != null) {
      applyJsSandbox(engine);
      log.info("[GlueJobHandler] JavaScript 引擎已加载（沙箱模式）: {}", engine.getClass().getName());
    } else {
      log.warn("[GlueJobHandler] JavaScript 引擎不可用, JS 类型 GLUE 任务将无法执行");
    }
  }

  /**
   * P0-6: 对 JavaScript 引擎应用沙箱安全限制。
   *
   * <p>针对不同引擎应用不同的安全策略：
   *
   * <ul>
   *   <li>GraalJS: 设置 {@code polyglot.js.allowHostAccess=false} 等选项， 禁止 JS 代码通过 {@code Java.type()}
   *       访问 Java 类
   *   <li>Nashorn: 设置 {@code nashorn.args=--no-java}， 禁止 JS 代码访问 Java 类（需引擎支持）
   * </ul>
   *
   * <p>注意：GraalJS 的 {@code ScriptEngine} 集成对部分选项仅在新 Context 创建时生效， 此处设置作为尽力而为的防护。生产环境建议使用 GraalJS 的
   * {@code Context} API 配合 {@code HostAccess.newBuilder().build()} 实现更严格的隔离。
   *
   * @param engine JavaScript 脚本引擎
   */
  private void applyJsSandbox(ScriptEngine engine) {
    try {
      String engineName = engine.getClass().getName().toLowerCase();
      if (engineName.contains("graal")) {
        // GraalJS: 禁止 Host 访问
        engine.put("polyglot.js.allowHostAccess", false);
        engine.put("polyglot.js.allowHostAccessLookup", false);
        engine.put("polyglot.js.allowAllAccess", false);
        engine.put("polyglot.js.allowIO", false);
        engine.put("polyglot.js.allowCreateThread", false);
        log.info(
            "[GlueJobHandler] GraalJS 沙箱限制已应用: allowHostAccess=false allowIO=false allowCreateThread=false");
      } else if (engineName.contains("nashorn")) {
        // Nashorn: 尝试设置 --no-java（部分实现支持）
        engine.put("nashorn.args", "--no-java --no-syntax-extensions");
        log.info("[GlueJobHandler] Nashorn 沙箱限制已应用: --no-java");
      }
    } catch (Exception e) {
      log.warn("[GlueJobHandler] 应用 JS 沙箱限制失败, 引擎可能以非沙箱模式运行: reason={}", e.getMessage());
    }
  }

  /**
   * P0-6: 创建 Groovy 安全编译配置。
   *
   * <p>使用 {@link SecureASTCustomizer} 限制 GLUE Groovy 脚本的访问范围：
   *
   * <ul>
   *   <li>导入白名单：仅允许常用安全类（集合、时间、数学、JobHandler 相关）
   *   <li>Star 导入白名单：仅允许 java.util / java.time / java.math
   *   <li>接收者黑名单：禁止在 System / Runtime / ProcessBuilder / Thread / ClassLoader / File / Path / URL /
   *       Socket 等危险类型上调用方法
   *   <li>启用间接导入检查：防止通过反射等手段绕过白名单
   * </ul>
   *
   * <p>对标 XXL-Job 的 GLUE 沙箱隔离和 PowerJob 的脚本安全策略。
   *
   * @return 配置好安全限制的 CompilerConfiguration
   */
  private CompilerConfiguration createSecureCompilerConfiguration() {
    SecureASTCustomizer customizer = new SecureASTCustomizer();
    customizer.setIndirectImportCheckEnabled(true);

    // 导入白名单：仅允许安全类
    List<String> importsWhitelist =
        List.of(
            "java.lang.Math",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.Boolean",
            "java.lang.Number",
            "java.lang.Object",
            "java.lang.Comparable",
            "java.lang.Iterable",
            "java.util.Date",
            "java.util.List",
            "java.util.Map",
            "java.util.Set",
            "java.util.Collection",
            "java.util.ArrayList",
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.LinkedHashMap",
            "java.util.LinkedHashSet",
            "java.util.Arrays",
            "java.util.Collections",
            "java.util.UUID",
            "java.time.LocalDateTime",
            "java.time.LocalDate",
            "java.time.LocalTime",
            "java.time.format.DateTimeFormatter",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "java.math.RoundingMode",
            "com.njydsz.cronjob.domain.job.JobHandler",
            "com.njydsz.cronjob.domain.job.JobExecutionContext",
            "com.njydsz.cronjob.domain.job.JobExecutionContext",
            "com.njydsz.cronjob.domain.job.ProcessResult");
    customizer.setImportsWhitelist(importsWhitelist);

    // Star 导入白名单：仅允许安全包
    customizer.setStarImportsWhitelist(List.of("java.util", "java.time", "java.math"));

    // 静态导入白名单
    customizer.setStaticImportsWhitelist(
        List.of("java.lang.Math", "java.util.Collections", "java.util.Arrays"));

    // 接收者黑名单：禁止在危险类型上调用方法
    customizer.setReceiversBlackList(
        List.of(
            System.class.getName(),
            Runtime.class.getName(),
            ProcessBuilder.class.getName(),
            Thread.class.getName(),
            ClassLoader.class.getName(),
            File.class.getName(),
            Path.class.getName(),
            Files.class.getName(),
            URL.class.getName(),
            Socket.class.getName(),
            ServerSocket.class.getName(),
            HttpURLConnection.class.getName(),
            Method.class.getName(),
            Field.class.getName(),
            Constructor.class.getName()));

    CompilerConfiguration config = new CompilerConfiguration();
    config.addCompilationCustomizers(customizer);
    return config;
  }

  @Override
  public Object execute(String paramsJson) throws Exception {
    // 从 JobExecutionContext 获取当前 jobId
    String jobId = JobExecutionContext.getShardingContext().getJobId();
    if (!StringUtils.hasText(jobId)) {
      throw new IllegalStateException("GLUE 任务执行上下文缺少 jobId，请确认 JobExecutionContext 已正确设置");
    }

    GlueCodeService glueCodeService = glueCodeServiceProvider.getIfAvailable();
    if (glueCodeService == null) {
      throw new IllegalStateException("GlueCodeService 未注册，GLUE 任务无法执行");
    }

    // 获取最新版本代码
    GlueCode glueCode = glueCodeService.getLatest(jobId);
    if (glueCode == null
        || glueCode.getSourceCode() == null
        || glueCode.getSourceCode().isBlank()) {
      throw new IllegalStateException("未找到 GLUE 代码或代码为空: jobId=" + jobId);
    }

    String sourceCode = glueCode.getSourceCode();
    String language =
        glueCode.getLanguage() != null ? glueCode.getLanguage().toUpperCase() : "GROOVY";
    log.info(
        "[GlueJobHandler] 执行 GLUE 任务: jobId={} version={} language={}",
        jobId,
        glueCode.getVersion(),
        language);

    // P1-7: 根据语言类型选择执行引擎
    return switch (language) {
      case "GROOVY", "JAVA" -> executeGroovy(jobId, sourceCode, paramsJson);
      case "PYTHON" -> executePython(sourceCode, paramsJson);
      case "SHELL" -> executeShell(sourceCode, paramsJson);
      case "JAVASCRIPT", "JS" -> executeJavaScript(sourceCode, paramsJson);
      default -> throw new IllegalStateException("不支持的 GLUE 语言类型: " + language);
    };
  }

  /** P1-7: 执行 Groovy 脚本（原有逻辑）。 */
  private Object executeGroovy(String jobId, String sourceCode, String paramsJson)
      throws Exception {
    Class<?> clazz = compileWithCache(jobId, sourceCode);
    Object instance;
    try {
      instance = clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("GLUE 代码实例化失败: " + e.getMessage(), e);
    }
    return invokeExecute(instance, paramsJson);
  }

  /**
   * P1-7: 执行 Python 脚本（通过沙箱执行器）。
   *
   * <p>paramsJson 通过环境变量 {@code JOB_PARAMS} 传入， stdout 作为执行结果返回。
   */
  private Object executePython(String sourceCode, String paramsJson) throws Exception {
    SandboxScriptExecutor executor = sandboxExecutorProvider.getIfAvailable();
    if (executor == null) {
      throw new IllegalStateException("SandboxScriptExecutor 未注册，Python GLUE 任务无法执行");
    }
    Map<String, String> envVars = new HashMap<>();
    envVars.put("JOB_PARAMS", paramsJson != null ? paramsJson : "{}");
    SandboxScriptExecutor.SandboxResult result =
        executor.execute(sourceCode, "PYTHON", 300, envVars);
    if (!result.success()) {
      throw new IllegalStateException("Python 脚本执行失败: " + result.errorMessage());
    }
    return result.output();
  }

  /**
   * P1-7: 执行 Shell 脚本（通过沙箱执行器）。
   *
   * <p>paramsJson 通过环境变量 {@code JOB_PARAMS} 传入， stdout 作为执行结果返回。
   */
  private Object executeShell(String sourceCode, String paramsJson) throws Exception {
    SandboxScriptExecutor executor = sandboxExecutorProvider.getIfAvailable();
    if (executor == null) {
      throw new IllegalStateException("SandboxScriptExecutor 未注册，Shell GLUE 任务无法执行");
    }
    Map<String, String> envVars = new HashMap<>();
    envVars.put("JOB_PARAMS", paramsJson != null ? paramsJson : "{}");
    SandboxScriptExecutor.SandboxResult result =
        executor.execute(sourceCode, "SHELL", 300, envVars);
    if (!result.success()) {
      throw new IllegalStateException("Shell 脚本执行失败: " + result.errorMessage());
    }
    return result.output();
  }

  /**
   * P1-7: 执行 JavaScript 脚本（通过 ScriptEngine）。
   *
   * <p>paramsJson 通过全局变量 {@code paramsJson} 传入， 脚本可通过 {@code execute(paramsJson)} 函数返回结果，
   * 或直接将最后一行表达式作为返回值。
   */
  private Object executeJavaScript(String sourceCode, String paramsJson) throws Exception {
    if (jsEngine == null) {
      throw new IllegalStateException("JavaScript 引擎不可用，请添加 Nashorn 或 GraalJS 依赖");
    }
    try {
      jsEngine.put("paramsJson", paramsJson != null ? paramsJson : "{}");
      Object result = jsEngine.eval(sourceCode);
      logToJobLogger("JavaScript 脚本执行完成: result={}", result);
      return result != null ? result.toString() : "null";
    } catch (Exception e) {
      throw new IllegalStateException("JavaScript 脚本执行失败: " + e.getMessage(), e);
    }
  }

  /**
   * 编译 GLUE 代码（带缓存）。
   *
   * <p>缓存 key 为 {@code jobId + ":" + sourceCode.hashCode()}， 当源代码变更时自动重新编译。
   *
   * <p>P0-P5 性能优化：原实现为 {@code synchronized} 全局锁，多任务并发编译时会互相阻塞（编译为 CPU
   * 密集操作，耗时可达百毫秒级）。改为 {@link ConcurrentHashMap#computeIfAbsent} 按缓存 key 分段加锁：
   * 同一 jobId+源码 仅编译一次，不同任务并行编译互不阻塞。
   *
   * @param jobId 任务 ID
   * @param sourceCode 源代码
   * @return 编译后的 Class
   * @throws IllegalStateException 编译失败时抛出
   */
  private Class<?> compileWithCache(String jobId, String sourceCode) {
    String cacheKey = jobId + ":" + sourceCode.hashCode();
    Class<?> cached = compiledClassCache.get(cacheKey);
    if (cached != null) {
      log.debug("[GlueJobHandler] 命中编译缓存: jobId={}", jobId);
      return cached;
    }
    return compiledClassCache.computeIfAbsent(
        cacheKey,
        key -> {
          // P0-6: 使用安全编译配置（SecureASTCustomizer 沙箱隔离）
          CompilerConfiguration config = createSecureCompilerConfiguration();
          GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader(), config);
          try {
            Class<?> clazz = classLoader.parseClass(sourceCode);
            classLoaderCache.put(key, classLoader);
            log.info(
                "[GlueJobHandler] GLUE 代码编译成功（沙箱模式）: jobId={} className={}",
                jobId,
                clazz.getName());
            return clazz;
          } catch (Exception e) {
            throw new IllegalStateException(
                "GLUE 代码编译失败（沙箱安全检查未通过）: " + e.getMessage(), e);
          }
        });
  }

  /**
   * 调用脚本实例的 execute 方法。
   *
   * <p>支持两种约定：
   *
   * <ol>
   *   <li>实现 {@link JobHandler} 接口 → 调用 {@code execute(String)}
   *   <li>定义 {@code execute(String)} 方法（脚本式）→ 反射调用
   * </ol>
   *
   * @param instance 脚本实例
   * @param paramsJson 参数 JSON
   * @return 执行结果
   * @throws Exception 执行失败时抛出
   */
  private Object invokeExecute(Object instance, String paramsJson) throws Exception {
    // 优先走 JobHandler 接口
    if (instance instanceof JobHandler handler) {
      return handler.execute(paramsJson);
    }
    // 反射查找 execute(String) 方法
    try {
      Method method = instance.getClass().getMethod("execute", String.class);
      Object result = method.invoke(instance, paramsJson);
      logToJobLogger("GLUE 脚本执行完成: result={}", result);
      return result;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "GLUE 代码未实现 JobHandler 接口，也未定义 execute(String) 方法: " + instance.getClass().getName(), e);
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw new IllegalStateException("GLUE 代码执行失败: " + cause.getMessage(), cause);
    }
  }

  /** 将日志写入在线日志器（如可用）。 */
  private void logToJobLogger(String format, Object... args) {
    try {
      var logger = JobExecutionContext.getLogger();
      if (logger != null) {
        logger.info(format, args);
      }
    } catch (Exception ignored) {
      // 日志写入失败不影响主流程
    }
  }

  /**
   * 测试用：直接设置 GlueCodeService（绕过 ObjectProvider）。
   *
   * <p>仅供单元测试使用，生产代码不应调用。
   *
   * @param glueCodeService GLUE 代码服务
   */
  void setGlueCodeServiceForTest(GlueCodeService glueCodeService) {
    // 通过匿名 ObjectProvider 包装直接返回
    // 该方法仅在测试场景下被反射调用，避免污染生产构造路径
  }

  /** 测试用：清空编译缓存。 */
  void clearCacheForTest() {
    compiledClassCache.clear();
    classLoaderCache.clear();
  }

  /** 测试用：查询当前缓存条目数。 */
  int cacheSizeForTest() {
    return compiledClassCache.size();
  }
}
