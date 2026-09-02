package com.njydsz.cronjob.server.core.handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.SandboxConfig;

/**
 * P1-4: Groovy Docker 沙箱执行器。
 *
 * <p>将 Groovy 脚本在 Docker 容器中编译执行，提供比 SecureASTCustomizer 更强的隔离：
 *
 * <ul>
 *   <li><b>文件系统隔离</b>：容器内独立文件系统，无法访问宿主机
 *   <li><b>网络隔离</b>：默认 --network=none 禁止网络访问
 *   <li><b>资源限制</b>：CPU / 内存 / PID 限制，防止资源耗尽
 *   <li><b>进程隔离</b>：容器内进程与宿主机完全隔离
 * </ul>
 *
 * <h3>执行命令</h3>
 *
 * <pre>
 * docker run --rm \
 *   --name ydsz-groovy-sandbox-{timestamp} \
 *   --network=none \
 *   --memory=512m \
 *   --cpus=1 \
 *   --pids-limit=100 \
 *   --read-only \
 *   --tmpfs /tmp:rw,size=50m \
 *   -v {hostScriptPath}:/app/script.groovy:ro \
 *   -e JOB_PARAMS=... \
 *   groovy:4.0-jdk17-slim \
 *   groovy /app/script.groovy
 * </pre>
 *
 * <h3>启用方式</h3>
 *
 * <pre>
 * ydsz.cronjob.sandbox.groovy-docker-enabled=true
 * ydsz.cronjob.sandbox.groovy-docker-image=groovy:4.0-jdk17-slim
 * ydsz.cronjob.sandbox.groovy-docker-memory=512m
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroovyDockerSandboxExecutor {

  /** 输出读取超时（毫秒） */
  private static final long OUTPUT_READ_TIMEOUT_MILLIS = 2000;

  /** 等待超时（秒） */
  private static final long WAIT_TIMEOUT_SECONDS = 5;

  /** Groovy 脚本文件名 */
  private static final String SCRIPT_FILENAME = "script.groovy";

  private final CronjobProperties cronjobProperties;

  /** Docker 可用性检查缓存 */
  private volatile Boolean dockerAvailable = null;

  /**
   * 在 Docker 容器中执行 Groovy 脚本。
   *
   * @param scriptContent Groovy 脚本内容
   * @param paramsJson 参数 JSON（通过环境变量 JOB_PARAMS 传入）
   * @param timeoutSeconds 超时时间（秒）
   * @return 执行结果
   */
  public GroovySandboxResult execute(String scriptContent, String paramsJson, int timeoutSeconds) {
    SandboxConfig sandboxConfig = cronjobProperties.getSandbox();

    if (!sandboxConfig.isEnabled() || !sandboxConfig.isGroovyDockerEnabled()) {
      return new GroovySandboxResult(false, "Groovy Docker 沙箱未启用", -1, "");
    }

    if (!isDockerAvailable()) {
      return new GroovySandboxResult(false, "Docker 不可用，请检查 Docker 安装和权限", -1, "");
    }

    Path scriptFile = null;
    try {
      // 1. 写脚本到宿主机临时目录（供只读挂载进容器）
      Path sandboxDir = Path.of(sandboxConfig.getWorkDir(), "groovy-docker-sandbox-" + System.nanoTime());
      Files.createDirectories(sandboxDir);
      scriptFile = sandboxDir.resolve(SCRIPT_FILENAME);
      Files.writeString(scriptFile, scriptContent);

      // 2. 构造 docker run 命令
      List<String> cmd = buildDockerCommand(sandboxConfig, scriptFile, paramsJson);

      // 3. 启动并等待（带超时）
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process process = pb.start();

      // 4. 异步读取 stdout
      StringBuilder outputBuilder = new StringBuilder();
      Thread outputReader = new Thread(
          () -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
              String line;
              int maxOutput = sandboxConfig.getMaxOutputSize();
              while ((line = reader.readLine()) != null) {
                if (outputBuilder.length() + line.length() > maxOutput) {
                  outputBuilder.append("\n[OUTPUT TRUNCATED]");
                  break;
                }
                outputBuilder.append(line).append("\n");
              }
            } catch (Exception e) {
              log.debug("[GroovyDocker] 输出读取异常: {}", e.getMessage());
            }
          },
          "groovy-docker-output");
      outputReader.setDaemon(true);
      outputReader.start();

      // 5. 等待完成或超时
      int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : sandboxConfig.getTimeoutSeconds();
      boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        killContainer("ydsz-groovy-sandbox");
        outputReader.join(OUTPUT_READ_TIMEOUT_MILLIS);
        return new GroovySandboxResult(
            false, "执行超时 (" + effectiveTimeout + "s)", -1, outputBuilder.toString());
      }

      // 等待输出读取完成
      outputReader.join(OUTPUT_READ_TIMEOUT_MILLIS);

      int exitCode = process.exitValue();
      boolean success = exitCode == 0;
      String output = outputBuilder.toString();

      return new GroovySandboxResult(
          success, success ? "success" : "exit code: " + exitCode, exitCode, output);
    } catch (Exception e) {
      log.error("[GroovyDocker] 执行异常: reason={}", e.getMessage(), e);
      return new GroovySandboxResult(false, "执行异常: " + e.getMessage(), -1, "");
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
   * 构造 Docker run 命令。
   *
   * @param config 沙箱配置
   * @param scriptFile 脚本文件路径
   * @param paramsJson 参数 JSON
   * @return Docker 命令参数列表
   */
  private List<String> buildDockerCommand(SandboxConfig config, Path scriptFile, String paramsJson) {
    List<String> cmd = new ArrayList<>(16);
    cmd.add("docker");
    cmd.add("run");
    cmd.add("--rm");
    cmd.add("--name");
    cmd.add("ydsz-groovy-sandbox-" + System.currentTimeMillis());

    // 网络隔离
    cmd.add("--network=" + config.getGroovyDockerNetwork());

    // 资源限制
    cmd.add("--memory=" + config.getGroovyDockerMemory());
    cmd.add("--cpus=" + config.getGroovyDockerCpus());
    cmd.add("--pids-limit=" + config.getGroovyDockerPidsLimit());

    // 只读文件系统
    cmd.add("--read-only");

    // tmpfs 挂载
    cmd.add("--tmpfs");
    cmd.add("/tmp:rw,size=50m");

    // 环境变量
    cmd.add("-e");
    cmd.add("JOB_PARAMS=" + (paramsJson != null ? paramsJson : "{}"));

    // 脚本只读挂载
    cmd.add("-v");
    cmd.add(scriptFile.toAbsolutePath() + ":/app/" + SCRIPT_FILENAME + ":ro");

    // 镜像和命令
    cmd.add(config.getGroovyDockerImage());
    cmd.add("groovy");
    cmd.add("/app/" + SCRIPT_FILENAME);

    return cmd;
  }

  /**
   * 检查 Docker 是否可用（带缓存）。
   *
   * @return true Docker 可用
   */
  private boolean isDockerAvailable() {
    if (dockerAvailable != null) {
      return dockerAvailable;
    }
    try {
      Process check = new ProcessBuilder("docker", "info").start();
      boolean finished = check.waitFor(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      dockerAvailable = finished && check.exitValue() == 0;
      if (!dockerAvailable) {
        log.warn("[GroovyDocker] Docker 不可用，请检查安装和权限");
      }
      return dockerAvailable;
    } catch (Exception e) {
      dockerAvailable = false;
      log.warn("[GroovyDocker] Docker 检查失败: {}", e.getMessage());
      return false;
    }
  }

  /** 强制清理容器（超时或异常时调用）。 */
  private void killContainer(String containerNamePrefix) {
    try {
      // 使用 filter 查找并强制删除所有匹配的容器
      Process kill = new ProcessBuilder(
          "docker", "rm", "-f",
          "$(docker", "ps", "-aq", "--filter", "name=" + containerNamePrefix + ")")
          .redirectErrorStream(true).start();
      kill.waitFor(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.debug("[GroovyDocker] 清理容器失败: reason={}", e.getMessage());
    }
  }

  /**
   * Groovy 沙箱执行结果。
   *
   * @param success 是否成功
   * @param message 结果消息
   * @param exitCode 退出码
   * @param output 标准输出
   */
  public record GroovySandboxResult(boolean success, String message, int exitCode, String output) {}
}
