package com.njydsz.agent.domain.gateway;

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * LLM 调用异常
 *
 * <p>LLM API 调用失败时抛出，包括网络超时、认证失败、模型不可用、响应解析错误等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LlmException extends SysException {

  private static final long serialVersionUID = 1L;

  /**
   * LLM 错误类型枚举
   *
   * <p>定义 LLM 调用失败的错误分类，包括网络超时、认证失败、模型不存在、限流、响应格式错误、Provider 内部错误和调用取消。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  public enum ErrorType {
    /** 网络超时 */
    NETWORK_TIMEOUT,
    /** 认证失败（API Key 无效） */
    AUTH_FAILED,
    /** 模型不存在 */
    MODEL_NOT_FOUND,
    /** 触发限流 */
    RATE_LIMITED,
    /** 响应格式无效 */
    INVALID_RESPONSE,
    /** Provider 内部错误 */
    PROVIDER_ERROR,
    /** 调用被取消（客户端断开/主动中断，不应触发重试或 Fallback） */
    CANCELED
  }

  /** 错误描述信息（SysException 精简构造器移除 String 入参后，由本类自行承载） */
  private final String message;

  private final ErrorType errorType;

  public LlmException(String message, ErrorType errorType) {
    super();
    this.message = message;
    this.errorType = errorType;
  }

  public LlmException(String message, ErrorType errorType, Throwable cause) {
    super();
    this.message = message;
    this.errorType = errorType;
    this.initCause(cause);
  }

  @Override
  public String getMessage() {
    return message != null ? message : super.getMessage();
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  /**
   * 将 LLM 错误类型映射为 Agent 模块统一错误码，供上层异常处理/网关网关透传使用。
   *
   * @return 对应的 Agent 异常码
   */
  public AgentExceptionCode toAgentErrorCode() {
    return switch (errorType) {
      case NETWORK_TIMEOUT, PROVIDER_ERROR, RATE_LIMITED, CANCELED ->
          AgentExceptionCode.LLM_CALL_FAILED;
      case AUTH_FAILED, MODEL_NOT_FOUND -> AgentExceptionCode.LLM_PROVIDER_NOT_CONFIGURED;
      case INVALID_RESPONSE -> AgentExceptionCode.LLM_RESPONSE_INVALID;
    };
  }
}
