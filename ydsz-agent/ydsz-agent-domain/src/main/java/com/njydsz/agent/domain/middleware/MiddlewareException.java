package com.njydsz.agent.domain.middleware;

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;

/**
 * 中间件执行异常。
 *
 * <p>其中断 Agent 执行管线时由中间件抛出（如安全护栏拒绝、限流触发）。
 * 包含面向用户的拒绝原因和面向开发者的内部诊断信息。
 *
 * <p><b>使用规范</b>：
 * <ul>
 *   <li>用户可见的拒绝信息设置 {@link #userMessage}（安全、合规场景）</li>
 *   <li>内部诊断设置 {@link #reason}（用于日志排查，不暴露给前端）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public class MiddlewareException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 面向用户的拒绝原因（经安全审查，可透传前端） */
  private final String userMessage;

  /** 错误码枚举 */
  private final AgentExceptionCode agentErrorCode;

  /**
   * 构造中间件异常。
   *
   * @param userMessage 面向用户的拒绝原因
   * @param errorCode 错误码枚举
   */
  public MiddlewareException(String userMessage, AgentExceptionCode errorCode) {
    super(errorCode);
    this.userMessage = userMessage;
    this.agentErrorCode = errorCode;
  }

  /**
   * 构造中间件异常（含内部原因）。
   *
   * @param userMessage 面向用户的拒绝原因
   * @param errorCode 错误码枚举
   * @param reason 内部诊断原因（用于日志）
   */
  public MiddlewareException(
      String userMessage, AgentExceptionCode errorCode, String reason) {
    super(errorCode);
    this.userMessage = userMessage;
    this.agentErrorCode = errorCode;
    this.setMessage(reason != null ? reason : userMessage);
  }

  /**
   * 构造中间件异常（含根因）。
   *
   * @param userMessage 面向用户的拒绝原因
   * @param errorCode 错误码枚举
   * @param reason 内部诊断原因
   * @param cause 根因异常
   */
  public MiddlewareException(
      String userMessage,
      AgentExceptionCode errorCode,
      String reason,
      Throwable cause) {
    super(errorCode, cause);
    this.userMessage = userMessage;
    this.agentErrorCode = errorCode;
    this.setMessage(reason != null ? reason : userMessage);
  }

  public String getUserMessage() {
    return userMessage;
  }

  public AgentExceptionCode getErrorCode() {
    return agentErrorCode;
  }
}
