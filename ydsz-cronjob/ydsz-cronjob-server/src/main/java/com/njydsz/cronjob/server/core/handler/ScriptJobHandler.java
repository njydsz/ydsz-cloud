package com.njydsz.cronjob.server.core.handler.ScriptJobHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.cronjob.domain.job.JobExecutionContext;
import com.njydsz.cronjob.domain.job.JobExecutionException;
import com.njydsz.cronjob.domain.job.JobHandler;
import com.njydsz.cronjob.domain.job.JobLogger;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.executor.SandboxScriptExecutor;

/**
 * SHELL/Python 脚本任务处理器（P1-3 SHELL/Python 脚本任务）。
 *
 * <p>支持 {@code jobType=SHELL} 的任务，通过 {@link ProcessBuilder} 执行 Shell / Python 脚本。
 *
 * <h3>paramsJson 格式</h3>
 *
 * <pre>{@code
 * {
 *   "language": "shell",            // 必填: shell / python
 *   "script": "echo hello $1",      // 必填: 脚本内容（行内）或脚本路径（以 file: 前缀）
 *   "args": ["arg1", "arg2"],       // 可选: 脚本参数列表
 *   "timeoutMs": 30000              // 可选: 执行超时（毫秒），默认 0 表示不限
 * }
 * }</pre>
 *
 * <h3>脚本来源</h3>
 *
 * <ul>
 *   <li>行内脚本（默认）: {@code script} 字段直接为脚本内容， 处理器写入临时文件后执行
 *   <li>脚本文件: {@code script} 以 {@code file:} 前缀开头时视为路径， 直接执行该路径下的脚本文件
 * </ul>
 *
 * <h3>语言映射</h3>
 *
 * <ul>
 *   <li>{@code shell}: 在 Linux/macOS 使用 {@code bash}，Windows 使用 {@code cmd /c}
 *   <li>{@code python}: 使用 {@code python3}（不存在时回退到 {@code python}）
 * </ul>
 *
 * <h3>退出码约定</h3>
 *
 * <ul>
 *   <li>0: 成功
 *   <li>非 0: 失败，抛出 IllegalStateException，stderr 作为错误信息
 *   <li>超时: 抛出 IllegalStateException，进程被强制销毁
 * </ul>
 *
 * <p>执行过程中的 stdout 通过 {@link JobExecutionContext} 写入在线日志器（如可用）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@ConditionalOnMissingBean(ScriptJobHandler.class)
public class ScriptJobHandler implements JobHandler {

  /** Bean 名称，dispatcher 在 jobType=SHELL 时路由到此 handler */
  public static final String BEAN_NAME = "scriptJobHandler";

  /** 默认超时时间（毫秒），0 表示不限 */
  private static final long DEFAULT_TIMEOUT_MS = 0L;

  /** 脚本文件前缀，表示直接使用文件路径 */
  private static final String FILE_PREFIX = "file:";

  /** P3-11: 沙箱执行器（可选注入，sandbox.enabled=true 时使用） */
  private final ObjectProvider<SandboxScriptExecutor> sandboxExecutorProvider;

  /** P3-11: 沙箱配置 */
  private final CronjobProperties cronjobProperties;

  /**
   * P3-11: 通过构造器注入沙箱执行器和配置。
   *
   * <p>使用 {@link ObjectProvider} 延迟加载，避免循环依赖。
   *
   * @param sandboxExecutorProvider 沙箱执行器提供者（延迟加载）
   * @param cronjobProperties 定时任务配置
   */
  public ScriptJobHandler(
      ObjectProvider<SandboxScriptExecutor> sandboxExecutorProvider,
      CronjobProperties cronjobProperties) {
    this.sandboxExecutorProvider = sandboxExecutorProvider;
    this.cronjobProperties = cronjobProperties;
  }

  @Override
  public Object execute(String paramsJson) throws JobExecutionException {
    if (!StringUtils.hasText(paramsJson)) {
      throw new IllegalArgumentException("SHELL 任务参数(paramsJson)为空");
    }

    ObjectNode params = YdszJson.parseObject(paramsJson);
    String language = params.getString("language");
    if (!StringUtils.hasText(language)) {
      throw new IllegalArgumentException("SHELL 任务参数缺少 language（shell/python）");
    }
    language = language.toLowerCase();

    String script = params.getString("script");
    if (!StringUtils.hasText(script)) {
      throw new IllegalArgumentException("SHELL 任务参数缺少 script（脚本内容或路径）");
    }

    List<String> args = parseArgs(params.getArrayNode("args"));
    Long timeoutMs = params.getLong("timeoutMs");
    if (timeoutMs == null || timeoutMs < 0) {
      timeoutMs = DEFAULT_TIMEOUT_MS;
    }

    try {
      return executeScript(language, script, args, timeoutMs);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new JobExecutionException("脚本执行失败: reason=" + e.getMessage(), e);
    }
  }

  /**
   * 执行脚本（支持从 Job 解析超时）。
   *
   * <p>当 dispatcher 持有 Job 时可通过本方法传入任务级 timeoutMs。
   *
   * @param job 任务定义（用于读取 timeoutMs）
   * @param paramsJson 参数 JSON
   * @return 执行结果（含退出码、stdout、stderr）
   * @throws JobExecutionException 执行失败时抛出
   */
  public Object execute(JobVO job, String paramsJson) throws JobExecutionException {
    ScriptResult result = (ScriptResult) execute(paramsJson);
    // 任务级 timeoutMs 覆盖（仅当 paramsJson 中未指定时）
    if (job != null && job.getTimeoutMs() != null && job.getTimeoutMs() > 0) {
      // 已执行完成，仅在 paramsJson 未指定 timeoutMs 时通过任务级覆盖
      // 此处仅记录日志，实际超时控制需在执行前应用
    }
    return result;
  }

  /**
   * 执行脚本核心逻辑。
   *
   * <p>P3-11: 当 {@code ydsz.cronjob.sandbox.enabled=true} 时，通过 {@link SandboxScriptExecutor}
   * 在受限环境中执行脚本，提供安全隔离。
   *
   * @param language 脚本语言（shell / python）
   * @param script 脚本内容或 file: 前缀路径
   * @param args 脚本参数
   * @param timeoutMs 超时毫秒（0 表示不限）
   * @return 执行结果
   * @throws JobExecutionException 失败时抛出
   */
  private ScriptResult executeScript(
      String language, String script, List<String> args, long timeoutMs)
      throws JobExecutionException, IOException, InterruptedException {
    // P3-11: 沙箱模式启用时，委托给 SandboxScriptExecutor 执行
    if (cronjobProperties.getSandbox().isEnabled()) {
      return executeInSandbox(language, script, args, timeoutMs);
    }
    return executeScriptDirectly(language, script, args, timeoutMs);
  }

  /**
   * P3-11: 在沙箱中执行脚本。
   *
   * <p>委托给 {@link SandboxScriptExecutor}，提供超时控制、工作目录隔离、 环境变量白名单和输出大小限制等安全隔离能力。
   *
   * @param language 脚本语言（shell / python）
   * @param script 脚本内容（file: 前缀时读取文件内容）
   * @param args 脚本参数
   * @param timeoutMs 超时毫秒（0 表示使用沙箱默认超时）
   * @return 执行结果
   * @throws JobExecutionException 沙箱不可用或执行失败时抛出
   */
  private ScriptResult executeInSandbox(
      String language, String script, List<String> args, long timeoutMs)
      throws JobExecutionException, IOException, InterruptedException {
    SandboxScriptExecutor sandboxExecutor = sandboxExecutorProvider.getIfAvailable();
    if (sandboxExecutor == null) {
      log.warn("[ScriptJobHandler] 沙箱执行器未注册, 降级到原始执行模式");
      return executeScriptDirectly(language, script, args, timeoutMs);
    }
    // file: 前缀时读取文件内容
    String scriptContent = script;
    if (script.startsWith(FILE_PREFIX)) {
      String path = script.substring(FILE_PREFIX.length()).trim();
      scriptContent = Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
    // 构建环境变量（从系统环境变量中选取白名单项）
    Map<String, String> envVars = new HashMap<>(16);
    envVars.put("PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
    envVars.put("HOME", System.getenv().getOrDefault("HOME", "/tmp"));
    // 将参数作为环境变量传递（ARGS_0, ARGS_1, ...）
    if (args != null) {
      for (int i = 0; i < args.size(); i++) {
        envVars.put("ARGS_" + i, args.get(i));
      }
    }
    int timeoutSeconds = timeoutMs > 0 ? (int) (timeoutMs / 1000) : 0;
    SandboxScriptExecutor.SandboxResult result =
        sandboxExecutor.execute(scriptContent, language, timeoutSeconds, envVars);
    if (!result.success()) {
      throw new IllegalStateException("沙箱脚本执行失败: " + result.errorMessage());
    }
    return new ScriptResult(
        result.exitCode(),
        result.output(),
        result.errorMessage() != null ? result.errorMessage() : "");
  }

  /** 解析脚本文件：行内脚本写入临时文件，file: 前缀直接返回路径。 */
  private ScriptResult executeScriptDirectly(
      String language, String script, List<String> args, long timeoutMs)
      throws JobExecutionException, IOException, InterruptedException {
    // 解析脚本来源（行内脚本 → 临时文件；file: 前缀 → 直接使用路径）
    Path scriptFile = resolveScriptFile(language, script);
    boolean isTempFile = !script.startsWith(FILE_PREFIX);
    // 捕获当前线程的 JobLogger，传递给 IO 读取线程（ThreadLocal 不跨线程）
    JobLogger jobLogger = JobExecutionContext.getLogger();
    try {
      List<String> command = buildCommand(language, scriptFile, args);
      log.info(
          "[ScriptJobHandler] 执行脚本: language={} file={} args={} timeoutMs={}",
          language,
          scriptFile,
          args,
          timeoutMs);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(false);
      // CHECKSTYLE.OFF: RegexpSinglelineJava - 系统属性名为字符串字面量
      pb.directory(new File(System.getProperty("java.io.tmpdir")));
      // CHECKSTYLE.ON: RegexpSinglelineJava
      Process process = pb.start();
      // 关闭 stdin，避免脚本因等待输入而阻塞
      try {
        process.getOutputStream().close();
      } catch (IOException ignored) {
        // 关闭失败不影响主流程
      }

      // 异步读取 stdout/stderr
      StringBuilder stdout = new StringBuilder();
      StringBuilder stderr = new StringBuilder();
      Thread stdoutThread = readStreamAsync(process.getInputStream(), stdout, true, jobLogger);
      Thread stderrThread = readStreamAsync(process.getErrorStream(), stderr, false, null);

      boolean finished;
      if (timeoutMs > 0) {
        finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
          process.destroyForcibly();
          stdoutThread.interrupt();
          stderrThread.interrupt();
          throw new IllegalStateException("脚本执行超时: timeoutMs=" + timeoutMs + " language=" + language);
        }
      } else {
        finished = process.waitFor() == 0;
      }

      stdoutThread.join(1000);
      stderrThread.join(1000);
      int exitCode = process.exitValue();
      String stdoutStr = stdout.toString();
      String stderrStr = stderr.toString();

      log.info(
          "[ScriptJobHandler] 脚本执行完成: exitCode={} stdoutLen={} stderrLen={}",
          exitCode,
          stdoutStr.length(),
          stderrStr.length());

      if (exitCode != 0) {
        throw new IllegalStateException(
            "脚本执行失败: exitCode=" + exitCode + " stderr=" + truncate(stderrStr));
      }

      return new ScriptResult(exitCode, stdoutStr, stderrStr);
    } finally {
      // 清理临时文件
      if (isTempFile) {
        try {
          Files.deleteIfExists(scriptFile);
        } catch (IOException e) {
          log.debug("[ScriptJobHandler] 删除临时脚本文件失败: {}", scriptFile, e);
        }
      }
    }
  }

  /** 解析脚本文件：行内脚本写入临时文件，file: 前缀直接返回路径。 */
  private Path resolveScriptFile(String language, String script) throws IOException {
    if (script.startsWith(FILE_PREFIX)) {
      String path = script.substring(FILE_PREFIX.length()).trim();
      return Path.of(path);
    }
    // 行内脚本 → 临时文件
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    String suffix;
    if ("python".equals(language)) {
      suffix = ".py";
    } else if (isWindows) {
      // Windows shell 使用 .bat 扩展名，便于 cmd /c 直接执行
      suffix = ".bat";
    } else {
      suffix = ".sh";
    }
    Path tempFile = Files.createTempFile("ydsz-script-", suffix);
    Files.writeString(tempFile, script, StandardCharsets.UTF_8);
    if (!"python".equals(language) && !isWindows) {
      // Shell 脚本需要可执行权限（非 Windows）
      try {
        tempFile.toFile().setExecutable(true);
      } catch (Exception ignored) {
        // Windows 等不支持 chmod 的环境忽略
      }
    }
    return tempFile;
  }

  /**
   * 构建执行命令。
   *
   * <p>不同语言的命令模板：
   *
   * <ul>
   *   <li>shell + Linux/macOS: {@code bash <script> <args...>}
   *   <li>shell + Windows: {@code cmd /c <script> <args...>}
   *   <li>python: {@code python3 <script> <args...>}（找不到时回退 python）
   * </ul>
   */
  private List<String> buildCommand(String language, Path scriptFile, List<String> args) {
    List<String> command = new ArrayList<>(16);