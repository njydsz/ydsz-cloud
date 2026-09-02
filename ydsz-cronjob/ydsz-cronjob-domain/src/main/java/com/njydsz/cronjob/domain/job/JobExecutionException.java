package com.njydsz.cronjob.domain.job;

/**
 * 任务执行异常
 *
 * <p>定时任务执行失败时抛出，包括业务逻辑异常、外部服务调用失败、
 * 超时等场景。替代原始的 {@code throws Exception} 以提供明确的异常契约。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class JobExecutionException extends Exception {

  private static final long serialVersionUID = 1L;

  /** 任务参数 JSON */
  private final String paramsJson;

  /**
   * 构造任务执行异常
   *
   * @param message 错误消息
   */
  public JobExecutionException(String message) {
    super(message);
    this.paramsJson = null;
  }

  /**
   * 构造任务执行异常（带原始异常）
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public JobExecutionException(String message, Throwable cause) {
    super(message, cause);
    this.paramsJson = null;
  }

  /**
   * 构造任务执行异常（带上下文）
   *
   * @param message 错误消息
   * @param paramsJson 任务参数 JSON
   * @param cause 原始异常
   */
  public JobExecutionException(String message, String paramsJson, Throwable cause) {
    super(String.format("%s | params=%s", message, paramsJson), cause);
    this.paramsJson = paramsJson;
  }

  public String getParamsJson() {
    return paramsJson;
  }
}
