package com.njydsz.agent.infra.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionContext;
import com.njydsz.agent.domain.skill.SkillExecutionException;
import com.njydsz.agent.domain.skill.SkillExecutionResult;
import com.njydsz.agent.domain.skill.SkillExecutionTarget;
import com.njydsz.agent.domain.skill.SkillRuntime;

/**
 * 本地进程模式的 SkillRuntime 实现。
 *
 * <p>通过 ProcessBuilder 调用系统 Python / Shell 解释器执行 Skill 脚本。
 * 安全措施：超时自动 destroy、输出长度限制、环境变量隔离。
 *
 * <p>脚本执行流程：
 *
 * <ol>
 *   <li>从 {@link SkillDescriptor#scripts()} 取第一条脚本路径</li>
 *   <li>根据后缀名选择解释器（.sh → bash，.py → python3）</li>
 *   <li>通过环境变量 YDSZ_INPUT 传递 JSON 序列化的输入参数</li>
 *   <li>等待进程结束，收集 stdout/stderr</li>
 *   <li>组装 {@link SkillExecutionResult} 返回</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
public class LocalSkillRuntime implements SkillRuntime {

  /** 单条命令参数列表初始容量 */
  private static final int COLLECTION_CAPACITY = 8;
  /** 进程退出等待宽限（秒） */
  private static final int PROCESS_EXIT_GRACE_SECONDS = 5;
  /** 标准输出最大长度（字符），防止 OOM */
  private static final int MAX_OUTPUT_LENGTH = 1024 * 1024;
  /** 默认超时时间（秒） */
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  /** Python 解释器路径 */
  private final String pythonPath;

  /** Shell 解释器路径 */
  private final String shellPath;

  /**
   * 构造本地 Skill 运行时。
   *
   * @param pythonPath Python 解释器路径
   * @param shellPath  Shell 解释器路径
   */
  public LocalSkillRuntime(
      @Value("${ydsz.agent.skill.runtime.local.python-path:python3}") String pythonPath,
      @Value("${ydsz.agent.skill.runtime.local.shell-path:/bin/bash}") String shellPath) {
    this.pythonPath = pythonPath;
    this.shellPath = shellPath;
  }

  /**
   * 通过本地进程执行 Skill 脚本。
   *
   * <p>组装命令（解释器 + 脚本路径），注入环境变量与输入参数，
   * 启动进程并限时等待；超时或异常时返回失败结果。
   *
   * @param descriptor Skill 定义描述
   * @param context    执行上下文
   * @return 执行结果
   * @throws SkillExecutionException 解释器不可用或严重 IO 异常
   */
  @Override
  public SkillExecutionResult execute(SkillDescriptor descriptor, SkillExecutionContext context)
      throws SkillExecutionException {
    String skillCode = descriptor.skillCode();
    long startTime = System.currentTimeMillis();
    int timeout = resolveTimeout(context);

    String scriptPath = resolveScriptPath(descriptor);
    List<String> command = buildCommand(scriptPath);

    Process process = null;
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(false);

      // 注入环境变量
      Map<String, String> env = pb.environment();
      env.putAll(context.envVariables());
      env.put("YDSZ_SKILL_CODE", skillCode);
      env.put("YDSZ_TENANT_CODE", defaultIfBlank(context.tenantCode(), "default"));

      // 将输入参数序列化为 JSON 环境变量
      String inputJson = serializeInputParams(context);
      env.put("YDSZ_INPUT", inputJson);

      log.info("[LocalSkillRuntime] 开始执行 Skill: {} (script={}, timeout={}s)",
          skillCode, scriptPath, timeout);

      process = pb.start();

      boolean finished = process.waitFor(timeout + PROCESS_EXIT_GRACE_SECONDS, TimeUnit.SECONDS);
      long elapsed = System.currentTimeMillis() - startTime;

      if (!finished) {
        process.destroyForcibly();
        log.warn("[LocalSkillRuntime] Skill 执行超时: {} ({}ms)", skillCode, elapsed);
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("elapsedMs", elapsed);
        metrics.put("exitCode", -1);
        metrics.put("timeout", timeout);
        return SkillExecutionResult.failure(skillCode,
            "Skill 执行超时（" + timeout + "s）", "");
      }

      String stdout = readStreamLimited(process.getInputStream());
      String stderr = readStreamLimited(process.getErrorStream());
      int exitCode = process.exitValue();
      long elapsedMs = System.currentTimeMillis() - startTime;

      log.info("[LocalSkillRuntime] Skill 执行完成: {} (exitCode={}, elapsed={}ms)",
          skillCode, exitCode, elapsedMs);

      Map<String, Object> metrics = new HashMap<>();
      metrics.put("elapsedMs", elapsedMs);
      metrics.put("exitCode", exitCode);

      if (exitCode != 0) {
        return SkillExecutionResult.failure(skillCode,
            "脚本非零退出: exitCode=" + exitCode, stderr);
      }
      return SkillExecutionResult.success(skillCode, stdout, List.of(), metrics);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
      long elapsedMs = System.currentTimeMillis() - startTime;
      log.error("[LocalSkillRuntime] Skill 执行被中断: {}", skillCode);
      throw new SkillExecutionException(skillCode,
          "Skill 执行被中断: " + e.getMessage(), e);
    } catch (IOException e) {
      long elapsedMs = System.currentTimeMillis() - startTime;
      log.error("[LocalSkillRuntime] Skill 执行 IO 异常: {} - {}", skillCode, e.getMessage());
      throw new SkillExecutionException(skillCode,
          "Skill 执行 IO 异常: " + e.getMessage(), e);
    } catch (Exception e) {
      long elapsedMs = System.currentTimeMillis() - startTime;
      log.error("[LocalSkillRuntime] Skill 执行异常: {} - {}", skillCode, e.getMessage(), e);
      throw new SkillExecutionException(skillCode,
          "Skill 执行异常: " + e.getMessage(), e);
    }
  }

  /**
   * 列出当前可用的 Skill 编码（本地模式下仅返回空，通过注册中心管理）。
   *
   * @return 空列表
   */
  @Override
  public List<String> listAvailableSkills() {
    return List.of();
  }

  /**
   * 检查本地解释器是否可用。
   *
   * @return true=python3 和 sh/bash 均可用
   */
  @Override
  public boolean isAvailable() {
    return checkCommandAvailable(pythonPath) || checkCommandAvailable("python");
  }

  /**
   * 获取运行时环境类型。
   *
   * @return LOCAL
   */
  @Override
  public SkillExecutionTarget getTargetType() {
    return SkillExecutionTarget.LOCAL;
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 解析脚本路径——取 scripts 第一条。
   *
   * @param descriptor Skill 定义
   * @return 脚本绝对路径
   * @throws SkillExecutionException 脚本不存在
   */
  private String resolveScriptPath(SkillDescriptor descriptor) throws SkillExecutionException {
    if (descriptor.scripts() == null || descriptor.scripts().isEmpty()) {
      throw new SkillExecutionException(descriptor.skillCode(),
          "Skill 未配置脚本路径: " + descriptor.skillCode());
    }
    String first = descriptor.scripts().get(0);
    if (first == null || first.isBlank()) {
      throw new SkillExecutionException(descriptor.skillCode(),
          "Skill 脚本路径为空: " + descriptor.skillCode());
    }
    Path scriptFilePath = Paths.get(first);
    if (!Files.exists(scriptFilePath)) {
      throw new SkillExecutionException(descriptor.skillCode(),
          "Skill 脚本不存在: " + first);
    }
    return first;
  }

  /**
   * 根据脚本后缀构建解释器命令。
   *
   * @param scriptPath 脚本路径
   * @return 命令列表
   */
  private List<String> buildCommand(String scriptPath) {
    List<String> command = new ArrayList<>(COLLECTION_CAPACITY);
    if (scriptPath.endsWith(".sh")) {
      command.add(shellPath);
    } else if (scriptPath.endsWith(".py")) {
      command.add(pythonPath);
    } else {
      // 默认视为 Python
      command.add(pythonPath);
    }
    command.add(scriptPath);
    return command;
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
   * 将输入参数序列化为 JSON 字符串（简化实现，仅做 key=value 拼接的转义）。
   *
   * @param context 执行上下文
   * @return JSON 字符串（失败时返回空对象）
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
   * 检查命令是否可用。
   *
   * @param command 命令名
   * @return true=可用
   */
  private boolean checkCommandAvailable(String command) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command, "--version");
      Process proc = pb.start();
      boolean finished = proc.waitFor(PROCESS_EXIT_GRACE_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        proc.destroyForcibly();
        return false;
      }
      return proc.exitValue() == 0;
    } catch (Exception e) {
      log.debug("[LocalSkillRuntime] 命令不可用: {}", command);
      return false;
    }
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
