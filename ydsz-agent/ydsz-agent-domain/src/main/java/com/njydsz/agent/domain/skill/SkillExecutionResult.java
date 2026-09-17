package com.njydsz.agent.domain.skill;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Skill 执行结果（值对象）
 *
 * <p>封装一次 Skill 执行的完整输出，包括成功/失败状态、标准输出/错误、
 * 产物文件列表和执行指标。
 *
 * @param skillCode    Skill 编码
 * @param isSuccess    执行是否成功
 * @param stdout       标准输出内容
 * @param stderr       标准错误内容
 * @param outputFiles  产物文件列表（路径 → 描述）
 * @param metrics      执行指标（elapsedMs / exitCode 等）
 * @param errorMessage 失败原因（isSuccess=false 时非空）
 * @param completedAt  执行完成时间
 * @author ydsz-team
 * @since 26.09.17
 */
public record SkillExecutionResult(
    String skillCode,
    boolean isSuccess,
    String stdout,
    String stderr,
    List<String> outputFiles,
    Map<String, Object> metrics,
    String errorMessage,
    LocalDateTime completedAt
) implements Serializable {

  private static final long serialVersionUID = 1L;

  public SkillExecutionResult {
    Objects.requireNonNull(skillCode, "skillCode 不能为 null");
    Objects.requireNonNull(completedAt, "completedAt 不能为 null");
    stdout = stdout != null ? stdout : "";
    stderr = stderr != null ? stderr : "";
    outputFiles = outputFiles != null ? List.copyOf(outputFiles) : List.of();
    metrics = metrics != null ? Map.copyOf(metrics) : Map.of();
    errorMessage = errorMessage != null ? errorMessage : "";
  }

  /**
   * 获取执行耗时（毫秒）。
   *
   * @return 耗时毫秒，未记录返回 0
   */
  public long getElapsedMs() {
    Object val = metrics.get("elapsedMs");
    return val instanceof Number ? ((Number) val).longValue() : 0L;
  }

  /**
   * 获取进程退出码。
   *
   * @return 退出码，未记录返回 -1
   */
  public int getExitCode() {
    Object val = metrics.get("exitCode");
    return val instanceof Number ? ((Number) val).intValue() : -1;
  }

  /**
   * 创建成功结果。
   *
   * @param skillCode Skill 编码
   * @param stdout    标准输出
   * @param files     产物文件
   * @param metrics   执行指标
   * @return 成功结果
   */
  public static SkillExecutionResult success(
      String skillCode, String stdout, List<String> files, Map<String, Object> metrics) {
    return new SkillExecutionResult(
        skillCode, true, stdout, "", files, metrics, "", LocalDateTime.now());
  }

  /**
   * 创建失败结果。
   *
   * @param skillCode Skill 编码
   * @param error     失败原因
   * @param stderr    标准错误输出
   * @return 失败结果
   */
  public static SkillExecutionResult failure(
      String skillCode, String error, String stderr) {
    return new SkillExecutionResult(
        skillCode, false, "", stderr != null ? stderr : "",
        Collections.emptyList(), Collections.emptyMap(), error, LocalDateTime.now());
  }
}
