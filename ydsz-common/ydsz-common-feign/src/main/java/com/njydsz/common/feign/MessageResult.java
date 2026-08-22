package com.njydsz.common.feign;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送结果 DTO（兼容旧 com.njydsz.common.feign.MessageResult）。
 *
 * <p>1.0.0 新增错误消息分层：
 *
 * <ul>
 *   <li>{@link #userMessage} — 用户友好消息，走 i18n 解析，前端直接展示
 *   <li>{@link #developerMessage} — 开发者调试信息，含异常类名 + 详情，前端可折叠展示或日志采集
 *   <li>{@link #retryAfter} — 建议重试等待秒数，取自 {@link
 *       com.njydsz.common.exception.enums.ExceptionCode#retryAfterSeconds()}
 * </ul>
 *
 * <p>{@link #errorMessage} 已标记 {@link Deprecated}，兼容旧调用方，新代码应使用分层字段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class MessageResult implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 是否发送成功 */
  private boolean success;

  /**
   * 错误信息（失败时填充）。
   *
   * @deprecated 使用 {@link #userMessage} + {@link #developerMessage} 分层替代。旧调用方读取此字段仍兼容，
   *     新代码应使用分层字段。
   */
  @Deprecated
  private String errorMessage;

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
   * @since 1.0.0
   */
  private String userMessage;

  /**
   * 开发者调试信息（含异常类名 + 详情，前端可折叠展示或日志采集）。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  private String developerMessage;

  /**
   * 建议重试等待秒数（单位秒，取自 {@link com.njydsz.common.exception.enums.ExceptionCode#retryAfterSeconds()}）。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  private Integer retryAfter;

  public boolean isSuccess() {
    return success;
  }

  public String getErrorMessage() {
    return errorMessage;
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

  /**
   * 全参数构造器（用于回执查询等场景）。
   *
   * @param success 是否发送成功
   * @param errorMessage 错误信息（已废弃，兼容旧调用方）
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
      String errorMessage,
      String traceId,
      String providerTraceId,
      String status,
      String errorCode,
      String userMessage,
      String developerMessage,
      Integer retryAfter) {
    this.success = success;
    this.errorMessage = errorMessage;
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
   * 构建失败结果。
   *
   * @deprecated 使用 {@link #fail(String, String, String, Integer)} 分层重载替代。
   * @param channel 通道（保留参数，当前不使用）
   * @param errorMessage 错误信息
   * @return 失败结果
   */
  @Deprecated
  public static MessageResult fail(String channel, String errorMessage) {
    return fail(channel, errorMessage, null);
  }

  /**
   * 构建失败结果（带错误码）。
   *
   * @deprecated 使用 {@link #fail(String, String, String, Integer)} 分层重载替代。
   * @param channel 通道（保留参数，当前不使用）
   * @param errorMessage 错误信息
   * @param errorCode 错误码（可为 null）
   * @return 失败结果
   */
  @Deprecated
  public static MessageResult fail(String channel, String errorMessage, String errorCode) {
    return fail(errorCode, errorMessage, errorMessage, 0);
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
    result.errorMessage = userMessage;
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

  /**
   * 全参数构造器（用于回执查询等场景）。
   *
   * @deprecated 使用 {@link #MessageResult(boolean, String, String, String, String, String, String, String, Integer)}
   *     全参构造器替代。
   * @param channel 通道（保留参数，当前不使用）
   * @param status 发送状态
   * @param providerTraceId 服务商追踪 ID
   * @param errorMessage 错误信息
   */
  @Deprecated
  public MessageResult(String channel, String status, String providerTraceId, String errorMessage) {
    this.success = "SUCCESS".equals(status);
    this.status = status;
    this.providerTraceId = providerTraceId;
    this.errorMessage = errorMessage;
  }
}
