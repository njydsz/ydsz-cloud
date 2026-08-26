package com.njydsz.cronjob.server.core.executor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.SandboxConfig;

/**
 * 沙箱脚本执行器（P3-11 脚本执行沙箱）。
 *
 * <p>在受限环境中执行 SHELL/GLUE 脚本，提供安全隔离：
 *
 * <ul>
 *   <li>超时控制：脚本执行超过指定时间后强制终止
 *   <li>工作目录隔离：在临时目录中执行，限制文件访问范围
 *   <li>环境变量白名单：仅传递指定的环境变量
 *   <li>输出捕获：捕获 stdout/stderr 并限制大小
 *   <li>进程隔离：使用 ProcessBuilder 独立进程执行
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxScriptExecutor {
  /** 等待超时（秒） */
  private static final long WAIT_TIMEOUT_SECONDS = 5;


  /** P3-3.3: 沙箱配置统一从 CronjobProperties 读取 */
  private final CronjobProperties cronjobProperties;

  /**
   * 在沙箱中执行脚本。
   *
   * @param scriptContent 脚本内容
   * @param scriptType 脚本类型: SHELL / PYTHON
   * @param timeoutSeconds 超时时间（秒）
   * @param envVars 环境变量（白名单传递）
   * @return 执行结果
   */
  public SandboxResult execute(
      String scriptContent, String scriptType, int timeoutSeconds, Map<String, String> envVars) {
    SandboxConfig sandbox = cronjobProperties.getSandbox();
    // P0-F6: Docker 沙箱优先（配置启用且宿主机 Docker 可用），否则进程沙箱
    if (sandbox.isDockerEnabled()) {
      if (isDockerAvailable()) {
        return executeInDocker(scriptContent, scriptType, timeoutSeconds, envVars, sandbox);
      }
      log.warn(
          "[Sandbox] docker-enabled=true 但 Docker 不可用, 降级进程沙箱: type={}", scriptType);
    }
    return executeInProcess(scriptContent, scriptType, timeoutSeconds, envVars, sandbox);
  }

  /**
   * P0-F6: 进程沙箱执行（原实现，抽取为独立方法便于 Docker 分支复用入口）。
   */
  private SandboxResult executeInProcess(
      String scriptContent,
      String scriptType,
      int timeoutSeconds,
      Map<String, String> envVars,
      SandboxConfig sandbox) {
    int defaultTimeoutSeconds = sandbox.getTimeoutSeconds();
    int maxOutputSize = sandbox.getMaxOutputSize();
    String workDir = sandbox.getWorkDir();

    Path scriptFile = null;
    try {
      // 创建临时工作目录
      Path sandboxDir = Path.of(workDir, "sandbox-" + System.nanoTime());
      Files.createDirectories(sandboxDir);

      // 写入脚本文件
      String fileExtension = "PYTHON".equalsIgnoreCase(scriptType) ? ".py" : ".sh";
      scriptFile = sandboxDir.resolve("script" + fileExtension);
      Files.writeString(scriptFile, scriptContent);
      scriptFile.toFile().setExecutable(true);

      // 构建执行命令
      ProcessBuilder pb;
      if ("PYTHON".equalsIgnoreCase(scriptType)) {
        pb = new ProcessBuilder("python3", scriptFile.toString());
      } else {
        pb = new ProcessBuilder("bash", scriptFile.toString());
      }
      pb.directory(sandboxDir.toFile());
      pb.redirectErrorStream(true);

      // 设置白名单环境变量
      pb.environment().clear();
      if (envVars != null) {
        pb.environment().putAll(envVars);
      }
      // 保留必要的 PATH
      pb.environment().put("PATH", System.getenv("PATH"));

      // 启动进程
      Process process = pb.start();
      int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : defaultTimeoutSeconds;

      // 读取输出（限制大小）
      String output = readOutput(process, maxOutputSize);

      // 等待完成或超时
      boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new SandboxResult(
            false, output, "Script timed out after " + effectiveTimeout + "s", -1);
      }

      int exitCode = process.exitValue();
      boolean success = exitCode == 0;
      String errorMsg = success ? null : "Script exited with code " + exitCode;
      return new SandboxResult(success, output, errorMsg, exitCode);
    } catch (Exception e) {
      log.error("[Sandbox] 脚本执行异常: type={} reason={}", scriptType, e.getMessage(), e);
      return new SandboxResult(false, "", e.getClass().getSimpleName() + ": " + e.getMessage(), -1);
    } finally {
      // 清理临时文件
      if (scriptFile != null) {
        try {
          Files.deleteIfExists(scriptFile);
          Files.deleteIfExists(scriptFile.getParent());
        } catch (Exception ignored) {
          // 清理失败不影响主流程
        }
      }
    }
  }

  /**
   * P0-F6: Docker 容器沙箱执行。
   *
   * <p>在隔离容器中执行 SHELL/PYTHON 脚本，提供比进程沙箱更强的隔离：
   *
   * <ul>
   *   <li>文件系统隔离：{@code --read-only} 只读根文件系统 + 脚本只读挂载
   *   <li>网络隔离：{@code --network=none}（默认禁网，防数据外泄）
   *   <li>资源限制：{@code --memory} / {@code --cpus} / {@code --pids-limit}（防 fork 炸弹）
   *   <li>权限降级：{@code --user=nobody}
   *   <li>临时写入：{@code --tmpfs} 挂载可写目录
   * </ul>
   *
   * <p>注意：Docker 可用性在入口预检（{@link #isDockerAvailable()}），不可用降级进程沙箱；
   * 容器启动后执行异常不再降级（避免脚本重复执行），直接返回失败。
   */
  private SandboxResult executeInDocker(
      String scriptContent,
      String scriptType,
      int timeoutSeconds,
      Map<String, String> envVars,
      SandboxConfig sandbox) {
    Path scriptFile = null;
    try {
      // 1. 写脚本到宿主机临时目录（供只读挂载进容器）
      Path sandboxDir = Path.of(sandbox.getWorkDir(), "docker-sandbox-" + System.nanoTime());
      Files.createDirectories(sandboxDir);
      String fileExtension = "PYTHON".equalsIgnoreCase(scriptType) ? ".py" : ".sh";
      scriptFile = sandboxDir.resolve("script" + fileExtension);
      Files.writeString(scriptFile, scriptContent);
      scriptFile.toFile().setExecutable(true);

      String containerScript = sandbox.getDockerWorkDir() + "/script" + fileExtension;
      String image =
          "PYTHON".equalsIgnoreCase(scriptType)
              ? sandbox.getDockerImage()
              : sandbox.getDockerShellImage();

      // 2. 构造 docker run 命令
      List<String> cmd = new ArrayList<>();
      cmd.add("docker");
      cmd.add("run");
      cmd.add("--rm");
      cmd.add("--name");
      cmd.add("ydsz-sandbox-" + System.nanoTime());
      cmd.add("--memory");
      cmd.add(sandbox.getDockerMemory());
      cmd.add("--cpus");
      cmd.add(sandbox.getDockerCpus());
      cmd.add("--pids-limit");
      cmd.add(String.valueOf(sandbox.getDockerPidsLimit()));
      cmd.add("--network");
      cmd.add(sandbox.getDockerNetwork());
      if (sandbox.getDockerUser() != null && !sandbox.getDockerUser().isBlank()) {
        cmd.add("--user");
        cmd.add(sandbox.getDockerUser());
      }
      if (sandbox.isDockerReadOnly()) {
        cmd.add("--read-only");
      }
      if (sandbox.getDockerTmpfsSize() != null && !sandbox.getDockerTmpfsSize().isBlank()) {
        cmd.add("--tmpfs");
        cmd.add(sandbox.getDockerTmpfsSize());
      }
      // 环境变量白名单（与进程沙箱一致：仅 JOB_PARAMS 等显式传入变量）
      if (envVars != null) {
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
          cmd.add("-e");
          cmd.add(entry.getKey() + "=" + entry.getValue());
        }
      }
      // 脚本只读挂载 + 执行命令
      cmd.add("-v");
      cmd.add(scriptFile.toAbsolutePath() + ":" + containerScript + ":ro");
      cmd.add(image);
      cmd.add("PYTHON".equalsIgnoreCase(scriptType) ? "python3" : "bash");
      cmd.add(containerScript);

      // 3. 启动并等待（带超时）
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      String output = readOutput(process, sandbox.getMaxOutputSize());
      int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : sandbox.getTimeoutSeconds();
      boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return new SandboxResult(
            false, output, "Docker sandbox timed out after " + effectiveTimeout + "s", -1);
      }
      int exitCode = process.exitValue();
      boolean success = exitCode == 0;
      return new SandboxResult(
          success, output, success ? null : "Script exited with code " + exitCode, exitCode);
    } catch (Exception e) {
      log.error("[Sandbox] Docker 执行异常: type={} reason={}", scriptType, e.getMessage(), e);
      return new SandboxResult(
          false, "", "Docker sandbox error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), -1);
    } finally {
      if (scriptFile != null) {
        try {
          Files.deleteIfExists(scriptFile);
          Files.deleteIfExists(scriptFile.getParent());
        } catch (Exception ignored) {
          // 清理失败不影响主流程
        }
      }
    }
  }

  /** 检查宿主机 Docker 是否可用（docker info 5s 超时）。 */
  private boolean isDockerAvailable() {
    try {
      Process process =
          new ProcessBuilder("docker", "info").redirectErrorStream(true).start();
      boolean finished = process.waitFor(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (Exception e) {
      log.debug("[Sandbox] Docker 可用性检查失败: reason={}", e.getMessage());
      return false;
    }
  }

  /** 读取进程输出（限制大小，避免内存溢出）。 */
  private String readOutput(Process process, int maxOutputSize) {
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (output.length() + line.length() > maxOutputSize) {
          output.append("\n[OUTPUT TRUNCATED]");
          break;
        }
        output.append(line).append("\n");
      }
    } catch (Exception e) {
      log.warn("[Sandbox] 读取输出异常: reason={}", e.getMessage());
    }
    return output.toString();
  }

  /**
   * 沙箱执行结果。
   *
   * @param success 是否成功
   * @param output 标准输出/错误输出
   * @param errorMessage 错误消息（成功时为 null）
   * @param exitCode 进程退出码
   */
  public record SandboxResult(boolean success, String output, String errorMessage, int exitCode) {}
}
