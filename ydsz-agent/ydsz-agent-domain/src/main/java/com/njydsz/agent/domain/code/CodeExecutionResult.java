package com.njydsz.agent.domain.code;

/**
 * 代码执行结果值对象（不可变 record）。
 *
 * <p>封装一次 Python 代码块沙箱执行的完整结果，无论成功还是失败均有对应字段可查。
 *
 * @param success 是否执行成功
 * @param output 标准输出内容（超长时已按 {@link #DEFAULT_MAX_OUTPUT_LENGTH} 截断）
 * @param error 错误信息，成功时为 {@code null}
 * @param durationMs 执行耗时（毫秒）
 * @param exitCode 进程退出码
 * @author ydsz-team
 * @since 26.09.07
 */
public record CodeExecutionResult(
    boolean success,
    String output,
    String error,
    long durationMs,
    int exitCode) {

  /** 输出最大长度默认值 */
  private static final int DEFAULT_MAX_OUTPUT_LENGTH = 5000;

  /**
   * 创建一个成功结果。
   *
   * @param output 标准输出内容
   * @param durationMs 执行耗时（毫秒）
   * @return 成功的执行结果
   */
  public static CodeExecutionResult success(String output, long durationMs) {
    return new CodeExecutionResult(true, truncateOutput(output), null, durationMs, 0);
  }

  /**
   * 创建一个失败结果。
   *
   * @param error 错误信息
   * @param durationMs 执行耗时（毫秒）
   * @param exitCode 进程退出码
   * @return 失败的执行结果
   */
  public static CodeExecutionResult failure(String error, long durationMs, int exitCode) {
    return new CodeExecutionResult(false, null, error, durationMs, exitCode);
  }

  /**
   * 创建一个超时结果。
   *
   * @param timeoutSeconds 超时秒数
   * @param durationMs 已执行耗时（毫秒）
   * @return 超时的执行结果
   */
  public static CodeExecutionResult timeout(int timeoutSeconds, long durationMs) {
    return new CodeExecutionResult(
        false, null, "代码执行超时（超过 " + timeoutSeconds + " 秒）", durationMs, -1);
  }

  /**
   * 截断输出到最大允许长度。
   *
   * @param output 原始输出
   * @return 截断后的输出
   */
  private static String truncateOutput(String output) {
    if (output == null) {
      return "";
    }
    if (output.length() <= DEFAULT_MAX_OUTPUT_LENGTH) {
      return output;
    }
    return output.substring(0, DEFAULT_MAX_OUTPUT_LENGTH)
        + "\n... (输出已截断，超过 " + DEFAULT_MAX_OUTPUT_LENGTH + " 字符)";
  }
}
