package com.njydsz.agent.domain.tool;

/**
 * 工具执行异常
 *
 * <p>工具执行器（{@link ToolExecutor}）执行失败时抛出，包括网络超时、参数无效、
 * 工具内部错误等场景。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ToolExecutionException extends Exception {

  private static final long serialVersionUID = 1L;

  /** 工具名称 */
  private final String toolName;

  /**
   * 构造工具执行异常
   *
   * @param message 错误消息
   */
  public ToolExecutionException(String message) {
    super(message);
    this.toolName = null;
  }

  /**
   * 构造工具执行异常（带原始异常）
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public ToolExecutionException(String message, Throwable cause) {
    super(message, cause);
    this.toolName = null;
  }

  /**
   * 构造工具执行异常（带工具名称）
   *
   * @param toolName 工具名称
   * @param message 错误消息
   * @param cause 原始异常
   */
  public ToolExecutionException(String toolName, String message, Throwable cause) {
    super(String.format("工具执行失败 | tool=%s | error=%s", toolName, message), cause);
    this.toolName = toolName;
  }

  public String getToolName() {
    return toolName;
  }
}
