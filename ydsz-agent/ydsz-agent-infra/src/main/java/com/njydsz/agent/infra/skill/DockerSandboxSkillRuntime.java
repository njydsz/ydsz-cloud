package com.njydsz.agent.infra.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionContext;
import com.njydsz.agent.domain.skill.SkillExecutionException;
import com.njydsz.agent.domain.skill.SkillExecutionResult;
import com.njydsz.agent.domain.skill.SkillExecutionTarget;
import com.njydsz.agent.domain.skill.SkillRuntime;

/**
 * Docker 沙箱模式的 SkillRuntime 实现。
 *
 * <p>通过 Docker CLI 启动隔离容器执行 Skill 脚本。复用
 * {@code DockerSandboxCodeExecutionService} 的安全模式：
 *
 * <ul>
 *   <li>网络隔离（--network=none，禁止所有出站流量）</li>
 *   <li>内存限制（--memory=256m）</li>
 *   <li>CPU 限制（--cpus=1.0）</li>
 *   <li>只读文件系统（--read-only，仅 /tmp 可写）</li>
 *   <li>定时强制销毁（timeout 兜底 + docker rm -f）</li>
 * </ul>
 *
 * <p>脚本通过容器内 COPY 映射或本地挂载方式提供；输入参数通过环境变量传入。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.agent.skill.runtime.sandbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class DockerSandboxSkillRuntime implements SkillRuntime {

  /** 命令参数列表初始容量 */
  private static final int COLLECTION_CAPACITY = 16;
  /** 进程退出等待宽限（秒） */
  private static final int PROCESS_EXIT_GRACE_SECONDS = 5;
  /** 进程强制销毁等待（秒） */
  private static final int PROCESS_DESTROY_WAIT_SECONDS = 3;
  /** 标准输出最大长度 */
  private static final int MAX_OUTPUT_LENGTH = 1024 * 1024;
  /** 默认超时时间（秒） */
  private static final int DEFAULT_TIMEOUT_SECONDS = 60;

  /** Docker 镜像名称 */
  private final String dockerImage;
  /** 内存限制 */
  private final String memoryLimit;
  /** CPU 限制 */
  private final String cpuLimit;

  /**
   * 构造 Docker 沙箱 Skill 运行时。
   *
   * @param dockerImage         Docker 镜像名
   * @param memoryLimit         内存限制
   * @param cpuLimit            CPU 限制
   */
  public DockerSandboxSkillRuntime(
      @Value("${ydsz.agent.skill.runtime.sandbox.docker-image:python:3.12-alpine}")
          String dockerImage,
      @Value("${ydsz.agent.skill.runtime.sandbox.memory-limit:256m}") String memoryLimit,
      @Value("${ydsz.agent.skill.runtime.sandbox.cpu-limit:1.0}") String cpuLimit) {
    this.dockerImage = dockerImage;
    this.memoryLimit = memoryLimit;
    this.cpuLimit = cpuLimit;
  }

  /**
   * 通过 Docker 容器隔离执行 Skill 脚本。
   *
   * <p>将脚本路径挂载到容器内，通过环境变量传入输入参数与执行上下文，
   * 限时等待容器退出；超时或异常时强制停止容器并返回失败结果。
   *
   * @param descriptor Skill 定义描述
   * @param context    执行上下文
   * @return 执行结果
   * @throws SkillExecutionException Docker 不可用或严重异常
   */
  @Override
  public SkillExecutionResult execute(SkillDescriptor descriptor, SkillExecutionContext context)
      throws SkillExecutionException {
    String skillCode = descriptor.skillCode();
    long startTime = System.currentTimeMillis();
    int timeout = resolveTimeout(context);

    if (descriptor.scripts() == null || descriptor.scripts().isEmpty()) {
      throw new SkillExecutionException(skillCode,
          "Skill 未配置脚本路径: " + skillCode);
    }
    String scriptPath = descriptor.scripts().get(0);

    String containerName = "ydsz-skill-" + System.nanoTime();
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
      command.add("/tmp:size=20m");
      command.add("--stop-timeout");
      command.add(String.valueOf(timeout));

      // 挂载脚本目录到容器
      java.nio.file.Path scriptFilePath = java.nio.file.Paths.get(scriptPath);
      java.nio.file.Path parentDir = scriptFilePath.getParent();
      if (parentDir != null) {
        command.add("-v");
        command.add(parentDir.toAbsolutePath() + ":/skill-scripts:ro");
      }

      // 环境变量注入
      command.add("-e");
      command.add("YDSZ_SKILL_CODE=" + skillCode);
      command.add("-e");
      command.add("YDSZ_TENANT_CODE=" + defaultIfBlank(context.tenantCode(), "default"));
      command.add("-e");
      command.add("YDSZ_INPUT=" + serializeInputParams(context));

      // 用户自定义环境变量
      for (Map.Entry<String, String> entry : context.envVariables().entrySet()) {
        command.add("-e");
        command.add(entry.getKey() + "=" + entry.getValue());
      }

      // 镜像 + 执行命令
      command.add(dockerImage);

      // 根据脚本后缀决定解释器
      if (scriptPath.endsWith(".sh")) {
        command.add("sh");
      } else {
        command.add("python3");
      }
      command.add("/skill-scripts/" + scriptFilePath.getFileName().toString());

      log.info("[DockerSandboxSkillRuntime] 启动沙箱容器: skill={}, timeout={}s, cmd={}",
          skillCode, timeout, command);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(false);
      dockerProcess = pb.start();

      boolean finished = dockerProcess.waitFor(
          timeout + PROCESS_EXIT_GRACE_SECONDS, TimeUnit.SECONDS);
      long elapsed = System.currentTimeMillis() - startTime;

      if (!finished) {
        forceStopContainer(containerName);
        if (dockerProcess.isAlive()) {
          dockerProcess.destroyForcibly();
        }
        log.warn("[DockerSandboxSkillRuntime] Skill 执行超时，已停止容器: {}", skillCode);
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("elapsedMs", elapsed);
        metrics.put("exitCode", -1);
        metrics.put("timeout", timeout);
        return SkillExecutionResult.failure(skillCode,
            "Skill 沙箱执行超时（" + timeout + "s）", "");
      }

      String stdout = readStreamLimited(dockerProcess.getInputStream());
      String stderr = readStreamLimited(dockerProcess.getErrorStream());
      int exitCode = dockerProcess.exitValue();
      long elapsedMs = System.currentTimeMillis() - startTime;

      log.info("[DockerSandboxSkillRuntime] 沙箱执行完成: {} (exitCode={}, elapsed={}ms)",
          skillCode, exitCode, elapsedMs);

      Map<String, Object> metrics = new HashMap<>();
      metrics.put("elapsedMs", elapsedMs);
      metrics.put("exitCode", exitCode);

      if (exitCode != 0) {
        return SkillExecutionResult.failure(skillCode,
            "沙箱非零退出: exitCode=" + exitCode, stderr);
      }
      return SkillExecutionResult.success(skillCode, stdout, List.of(), metrics);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      forceStopContainer(containerName);
      if (dockerProcess != null && dockerProcess.isAlive()) {
        dockerProcess.destroyForcibly();
      }
      log.error("[DockerSandboxSkillRuntime] Skill 被中断: {}", skillCode);
      throw new SkillExecutionException(skillCode, "沙箱执行被中断", e);
    } catch (IOException e) {
      log.error("[DockerSandboxSkillRuntime] Docker IO 异常: {} - {}", skillCode, e.getMessage());
      throw new SkillExecutionException(skillCode,
          "Docker 沙箱 IO 异常: " + e.getMessage(), e);
    } catch (Exception e) {
      log.error("[DockerSandboxSkillRuntime] 沙箱执行异常: {} - {}", skillCode, e.getMessage(), e);
      throw new SkillExecutionException(skillCode,
          "沙箱执行异常: " + e.getMessage(), e);
    }
  }

  /**
   * 列出可用 Skill（沙箱模式通过注册中心管理）。
   *
   * @return 空列表
   */
  @Override
  public List<String> listAvailableSkills() {
    return List.of();
  }

  /**
   * 检查 Docker 守护进程是否可达。
   *
   * @return true=可用
   */
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
      log.info("[DockerSandboxSkillRuntime] Docker 不可用: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 获取运行时环境类型。
   *
   * @return SANDBOX
   */
  @Override
  public SkillExecutionTarget getTargetType() {
    return SkillExecutionTarget.SANDBOX;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 强制停止并移除 Docker 容器。
   *
   * @param containerName 容器名称
   */
  private void forceStopContainer(String containerName) {
    try {
      Process stopProcess = new ProcessBuilder(
          "docker", "stop", "--time=1", containerName).start();
      boolean stopped = stopProcess.waitFor(PROCESS_DESTROY_WAIT_SECONDS, TimeUnit.SECONDS);
      if (!stopped) {
        stopProcess.destroyForcibly();
      }
      log.info("[DockerSandboxSkillRuntime] 已停止容器: {}, success={}", containerName, stopped);
    } catch (Exception e) {
      log.warn("[DockerSandboxSkillRuntime] 停止容器失败: {} - {}",
          containerName, e.getMessage());
    }
  }

  /**
   * 读取流内容（带长度限制）。
   *
   * @param inputStream 输入流
   * @return 字符串内容
   * @throws IOException IO 异常
   */
  private String readStreamLimited(InputStream inputStream) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (sb.length() + line.length() > MAX_OUTPUT_LENGTH) {
          sb.append("\n... [OUTPUT TRUNCATED]");
          break;
        }
        sb.append(line).append("\n");
      }
    }
    return sb.toString().trim();
  }

  /**
   * 解析超时（优先使用上下文配置，否则默认值）。
   *
   * @param context 执行上下文
   * @return 超时秒数
   */
  private int resolveTimeout(SkillExecutionContext context) {
    if (context.timeoutMs() > 0) {
      return (int) Math.min(context.timeoutMs() / 1000, 600);
    }
    return DEFAULT_TIMEOUT_SECONDS;
  }

  /**
   * 将输入参数序列化为 JSON 字符串。
   *
   * @param context 执行上下文
   * @return JSON 字符串
   */
  private String serializeInputParams(SkillExecutionContext context) {
    Map<String, Object> params = context.inputParams();
    if (params == null || params.isEmpty()) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : params.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
      Object val = entry.getValue();
      if (val == null) {
        sb.append("null");
      } else if (val instanceof Number || val instanceof Boolean) {
        sb.append(val);
      } else {
        sb.append("\"").append(escapeJson(val.toString())).append("\"");
      }
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }

  /**
   * 简易 JSON 字符串转义。
   *
   * @param value 原始字符串
   * @return 转义后字符串
   */
  private String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  /**
   * 空值时返回默认值。
   *
   * @param value        原始值
   * @param defaultValue 默认值
   * @return 非空返回原值
   */
  private String defaultIfBlank(String value, String defaultValue) {
    return (value != null && !value.isBlank()) ? value : defaultValue;
  }
}
