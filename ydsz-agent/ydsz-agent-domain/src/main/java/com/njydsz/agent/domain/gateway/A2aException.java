package com.njydsz.agent.domain.gateway;

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * A2A 协议调用异常。
 *
 * <p>A2A Client 调用远程 Agent 失败时抛出，包括网络超时、协议解析错误、Task 失败等。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public class A2aException extends SysException {

  private static final long serialVersionUID = 1L;

  /**
   * A2A 错误类型枚举。
   */
  public enum A2aErrorType {
    /** 网络超时 */
    NETWORK_TIMEOUT,
    /** 协议错误（响应格式不符） */
    PROTOCOL_ERROR,
    /** 认证失败（Agent 拒绝访问） */
    AUTH_FAILED,
    /** Task 被远程 Agent 拒绝 */
    TASK_REJECTED,
    /** Task 执行失败 */
    TASK_FAILED,
    /** 远程 Agent 不可用 */
    AGENT_UNAVAILABLE,
    /** 轮询超时 */
    POLL_TIMEOUT,
    /** 取消 */
    CANCELED
  }

  private final String message;
  private final A2aErrorType errorType;

  public A2aException(String message, A2aErrorType errorType) {
    super();
    this.message = message;
    this.errorType = errorType;
  }

  public A2aException(String message, A2aErrorType errorType, Throwable cause) {
    super();
    this.message = message;
    this.errorType = errorType;
    this.initCause(cause);
  }

  @Override
  public String getMessage() {
    return message != null ? message : super.getMessage();
  }

  public A2aErrorType getErrorType() {
    return errorType;
  }

  public AgentExceptionCode toAgentErrorCode() {
    return switch (errorType) {
      case NETWORK_TIMEOUT, AGENT_UNAVAILABLE, POLL_TIMEOUT, CANCELED ->
          AgentExceptionCode.A2A_CALL_FAILED;
      case AUTH_FAILED, TASK_REJECTED, TASK_FAILED, PROTOCOL_ERROR ->
          AgentExceptionCode.A2A_PROTOCOL_ERROR;
    };
  }
}
