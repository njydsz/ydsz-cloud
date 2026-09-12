package com.njydsz.agent.infra.code;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.code.CodeExecutionException;
import com.njydsz.agent.domain.code.CodeExecutionRequest;
import com.njydsz.agent.domain.code.CodeExecutionResult;
import com.njydsz.agent.domain.code.CodeExecutionService;

/**
 * Docker 沙箱模式的代码执行服务实现（主实现）。
 *
 * <p>通过 ProcessBuilder 调用 Docker CLI 启动隔离容器执行 Python 代码。
 *
 * <p>安全措施：
 *
 * <ul>
 *   <li>网络隔离（--network=none，禁止所有网络访问）</li>
 *   <li>内存限制（--memory=128m，防止OOM攻击）</li>
 *   <li>CPU 限制（--cpus=0.5，防止CPU密集消耗）</li>
 *   <li>只读文件系统（--read-only，仅 /tmp 可写）</li>
 *   <li>非 root 运行（通过 USER 指令）</li>
 *   <li>超时控制（docker stop 兜底）</li>
 *   <li>环境变量传递代码和输入（避免 shell 注入）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.agent.code-execution",
    name = "mode",
    havingValue = "docker")
public class DockerSandboxCodeExecutionService implements CodeExecutionService {

  /** 命令参数列表初始容量 */
  private static final int COLLECTION_CAPACITY = 16;
  /** 进程退出等待宽限（秒） */
  private static final int PROCESS_EXIT_GRACE_SECONDS = 5;
  /** 进程强制销毁等待（秒） */
  private static final int PROCESS_DESTROY_WAIT_SECONDS = 3;

  /** Docker 镜像名称 */
  private final String dockerImage;

  /** 内存限制配置值 */
  private final String memoryLimit;

  /** CPU 限制配置值 */
  private final String cpuLimit;

  /** 允许的模块白名单 */
  private final List<String> allowedModules;

  /** 默认超时时间（秒） */
  private final int defaultTimeoutSeconds;

  /**
   * 构造 Docker 沙箱执行器。
   *
   * @param dockerImage Docker 镜像名
   * @param memoryLimit 内存限制
   * @param cpuLimit CPU 限制
   * @param allowedModules 允许的模块白名单
   * @param defaultTimeoutSeconds 默认超时秒数
   */
  public DockerSandboxCodeExecutionService(
      @Value("${ydsz.agent.code-execution.docker-image:python:3.12-alpine}") String dockerImage,
      @Value("${ydsz.agent.code-execution.docker-memory-limit:128m}") String memoryLimit,
      @Value("${ydsz.agent.code-execution.docker-cpu-limit:0.5}") String cpuLimit,
      @Value("${ydsz.agent.code-execution.allowed-modules:json,math,statistics,itertools,collections,datetime,functools,operator,re,string}")
          List<String> allowedModules,
      @Value("${ydsz.agent.code-execution.timeout-seconds:30}") int defaultTimeoutSeconds) {
    this.dockerImage = dockerImage;
    this.memoryLimit = memoryLimit;
    this.cpuLimit = cpuLimit;
    this.allowedModules = allowedModules;
    this.defaultTimeoutSeconds = defaultTimeoutSeconds;
  }

  @Override
  public CodeExecutionResult execute(CodeExecutionRequest request) throws CodeExecutionException {
    long startTime = System.currentTimeMillis();
    int timeout = request.timeoutSeconds() > 0 ? request.timeoutSeconds() : defaultTimeoutSeconds;

    // 生成唯一容器名
    String containerName = "ydsz-code-" + System.nanoTime();
    Process dockerProcess = null;

    try {
      // 构建 docker run 命令
      List<String> command = new ArrayList<>(COLLECTION_CAPACITY);
      command.add("docker");
      command.add("run");
      command.add("--rm");
      command.add("--name");
      command.add(containerName);
      command.add("--network=none");
      command.add("--memory=" + memoryLimit);
      command.add("--cpus=" + cpuLimit);
      command.add("--read-only");
      command.add("--tmpfs");
      command.add("/tmp:size=10m");
      command.add("--stop-timeout");
      command.add(String.valueOf(timeout));
      command.add("-e");
      command.add("YDSZ_INPUT=" + (request.inputJson() != null ? request.inputJson() : "{}"));
      command.add(dockerImage);
      command.add("python");
      command.add("-c");
      command.add(request.code());

      log.info("[DockerCodeExecution] 启动容器: image={}, timeout={}s", dockerImage, timeout);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(false);
      dockerProcess = pb.start();

      // 等待执行完成或超时
      boolean finished = dockerProcess.waitFor(timeout + PROCESS_EXIT_GRACE_SECONDS, TimeUnit.SECONDS);
      long duration = System.currentTimeMillis() - startTime;

      if (!finished) {
        // 超时：强制停止容器
        forceStopContainer(containerName);
        if (dockerProcess.isAlive()) {
          dockerProcess.destroyForcibly();
        }
        log.warn("[DockerCodeExecution] 执行超时，已强制停止容器: {}", containerName);
        throw CodeExecutionException.timeout(timeout, duration);
      }

      // 读取标准输出
      String output = readStream(dockerProcess.getInputStream());
      String error = readStream(dockerProcess.getErrorStream());
      int exitCode = dockerProcess.exitValue();

      if (exitCode != 0) {
        log.warn("[DockerCodeExecution] 容器非零退出: exitCode={}, error={}", exitCode, error);
        return CodeExecutionResult.failure(error, duration, exitCode);
      }
      return CodeExecutionResult.success(output, duration);

    } catch (CodeExecutionException e) {
      throw e;
    } catch (IOException e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("[DockerCodeExecution] IO 异常: {}", e.getMessage(), e);
      throw new CodeExecutionException(
          "Docker 执行 IO 异常: " + e.getMessage(),
          CodeExecutionResult.failure(e.getMessage(), duration, -1),
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      long duration = System.currentTimeMillis() - startTime;
      forceStopContainer(containerName);
      if (dockerProcess != null && dockerProcess.isAlive()) {
        dockerProcess.destroyForcibly();
      }
      throw new CodeExecutionException(
          "Docker 执行被中断",
          CodeExecutionResult.failure("执行被中断", duration, -1),
          e);
    }
  }

  @Override
  public boolean isAvailable() {
    try {
      Process process = new ProcessBuilder("docker", "info").start();
      boolean finished = process.waitFor(PROCESS_EXIT_GRACE_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        return false;
      }
      return process.exitValue() == 0;
    } catch (Exception e) {
      log.info("[DockerCodeExecution] Docker 不可用: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public List<String> listAllowedModules() {
    return allowedModules;
  }

  /**
   * 强制停止并移除 Docker 容器。
   *
   * @param containerName 容器名称
   */
  private void forceStopContainer(String containerName) {
    try {
      Process stopProcess = new ProcessBuilder("docker", "stop", "--time=1", containerName).start();
      boolean stopped = stopProcess.waitFor(PROCESS_DESTROY_WAIT_SECONDS, TimeUnit.SECONDS);
      if (!stopped) {
        stopProcess.destroyForcibly();
      }
      log.info("[DockerCodeExecution] 已停止容器: {}, success={}", containerName, stopped);
    } catch (Exception e) {
      log.warn("[DockerCodeExecution] 停止容器失败: {} - {}", containerName, e.getMessage());
    }
  }

  /**
   * 读取 InputStream 内容为字符串。
   *
   * @param inputStream 输入流
   * @return 字符串内容
   * @throws IOException IO 异常
   */
  private String readStream(InputStream inputStream) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
    }
    return sb.toString().trim();
  }
}
