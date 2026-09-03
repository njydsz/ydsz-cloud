package com.njydsz.common.feign;

import java.io.Serial;
import java.io.Serializable;

/**
 * 消息发送结果 DTO（兼容旧 com.njydsz.common.feign.MessageResult）。 *
 * <p>错误消息分层：
 *
 * <ul>
 *   <li>{@link #userMessage} — 用户友好消息，走 i18n 解析，前端直接展示
 *   <li>{@link #developerMessage} — 开发者调试信息，含异常类名 + 详情，前端可折叠展示或日志采集
 *   <li>{@link #retryAfter} — 建议重试等待秒数，取自 {@link
 *       com.njydsz.common.exception.enums.ExceptionCode#retryAfterSeconds()}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class MessageResult implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 是否发送成功 */
  private boolean success;

  /** 消息追踪 ID */
  private String traceId;

  /** 服务商追踪 ID（回执查询用） */
  private String providerTraceId;

  /** 发送状态（SUCCESS / FAILED / UNKNOWN） */
  private String status;

  /**
   * 错误码（失败时填充，便于前端/客户端识别错误类别）。
   *
   * <p>取值来自 {@link com.njydsz.message.domain.enums.MessageExceptionCode#getCode()}， 为 null 表示无细分错误码（兼容旧调用方）。
   */
  private String errorCode;

  /**
   * 用户友好消息（走 i18n 解析，前端直接展示）。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  private String userMessage;

  /**
   * 开发者调试信息（含异常类名 + 详情，前端可折叠展示或日志采集）。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  private String developerMessage;

  /**
   * 建议重试等待秒数（单位秒，取自 {@link com.njydsz.common.exception.enums.ExceptionCode#retryAfterSeconds()}）。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  private Integer retryAfter;

  /** 无参构造器（Lombok {@code @NoArgsConstructor} 生成失败时的手动回退） */
  public MessageResult() {}

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getProviderTraceId() {
    return providerTraceId;
  }

  public void setProviderTraceId(String providerTraceId) {
    this.providerTraceId = providerTraceId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getUserMessage() {
    return userMessage;
  }

  public void setUserMessage(String userMessage) {
    this.userMessage = userMessage;
  }

  public String getDeveloperMessage() {
    return developerMessage;
  }

  public void setDeveloperMessage(String developerMessage) {
    this.developerMessage = developerMessage;
  }

  public Integer getRetryAfter() {
    return retryAfter;
  }

  public void setRetryAfter(Integer retryAfter) {
    this.retryAfter = retryAfter;
  }

  /**
   * 全参数构造器（用于回执查询等场景）。
   *
   * @param success 是否发送成功
   * @param traceId 消息追踪 ID
   * @param providerTraceId 服务商追踪 ID
   * @param status 发送状态
   * @param errorCode 错误码
   * @param userMessage 用户友好消息
   * @param developerMessage 开发者调试信息
   * @param retryAfter 建议重试等待秒数
   */
  public MessageResult(
      boolean success,
      String traceId,
      String providerTraceId,
      String status,
      String errorCode,
      String userMessage,
      String developerMessage,
      Integer retryAfter) {
    this.success = success;
    this.traceId = traceId;
    this.providerTraceId = providerTraceId;
    this.status = status;
    this.errorCode = errorCode;
    this.userMessage = userMessage;
    this.developerMessage = developerMessage;
    this.retryAfter = retryAfter;
  }

  /**
   * 构建成功结果。
   *
   * @param channel 通道（保留参数，当前不使用）
   * @param traceId 追踪 ID
   * @return 成功结果
   */
  public static MessageResult ok(String channel, String traceId) {
    MessageResult result = new MessageResult();
    result.success = true;
    result.traceId = traceId;
    result.status = "SUCCESS";
    return result;
  }

  /**
   * 构建失败结果（分层消息版）。
   *
   * <p>新增分层字段：userMessage（用户友好）/ developerMessage（调试详情）/ retryAfter（建议重试等待秒数）。
   *
   * @param errorCode 错误码（可为 null）
   * @param userMessage 用户友好消息（走 i18n 解析）
   * @param developerMessage 开发者调试信息（含异常类名 + 详情）
   * @param retryAfter 建议重试等待秒数（单位秒，可为 null）
   * @return 失败结果
   */
  public static MessageResult fail(
      String errorCode, String userMessage, String developerMessage, Integer retryAfter) {
    MessageResult result = new MessageResult();
    result.success = false;
    result.userMessage = userMessage;
    result.developerMessage = developerMessage;
    result.retryAfter = retryAfter;
    result.errorCode = errorCode;
    result.status = "FAILED";
    return result;
  }

  /**
   * 构建失败结果（分层消息版，带通道参数）。
   *
   * <p>新增分层字段：userMessage（用户友好）/ developerMessage（调试详情）/ retryAfter（建议重试等待秒数）。 通道参数为保留参数，当前不参与序列化。
   *
   * @param channel 通道（保留参数，当前不使用）
   * @param errorCode 错误码（可为 null）
   * @param userMessage 用户友好消息（走 i18n 解析）
   * @param developerMessage 开发者调试信息（含异常类名 + 详情）
   * @param retryAfter 建议重试等待秒数（单位秒，可为 null）
   * @return 失败结果
   */
  public static MessageResult fail(
      String channel,
      String errorCode,
      String userMessage,
      String developerMessage,
      Integer retryAfter) {
    return fail(errorCode, userMessage, developerMessage, retryAfter);
  }

}
