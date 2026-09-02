package com.njydsz.workflow.domain.exception;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * 工作流模块业务异常基类
 *
 * <p>所有工作流模块抛出的异常均应继承本类，以获得：
 *
 * <ul>
 *   <li>统一的错误码体系（{@link WorkflowExceptionCode}）
 *   <li>语义化的错误消息（通过 i18n 消息键）
 *   <li>正确的 HTTP 状态码映射（400/403/404/500 等）
 *   <li>异常分类标记（WORKFLOW 类别，便于监控告警）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 简单抛出
 * throw new WorkflowException(WorkflowExceptionCode.INSTANCE_NOT_FOUND);
 *
 * // 带原始异常
 * throw new WorkflowException(WorkflowExceptionCode.TASK_NOT_FOUND, cause);
 *
 * // 带自定义消息
 * throw new WorkflowException(WorkflowExceptionCode.ILLEGAL_STATE_TRANSITION, "当前状态不允许该操作");
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see WorkflowExceptionCode
 * @see BusinessException
 */
public class WorkflowException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /**
   * 使用工作流异常码构造异常
   *
   * @param exceptionCode 工作流异常码
   */
  public WorkflowException(WorkflowExceptionCode exceptionCode) {
    super(exceptionCode);
  }

  /**
   * 使用工作流异常码和原始异常构造异常
   *
   * @param exceptionCode 工作流异常码
   * @param cause 原始异常
   */
  public WorkflowException(WorkflowExceptionCode exceptionCode, Throwable cause) {
    super(exceptionCode, cause);
  }

  /**
   * 使用工作流异常码和自定义消息构造异常
   *
   * <p>保留用于需要补充动态上下文（如具体实例 ID、任务 ID）的场景。
   *
   * @param exceptionCode 工作流异常码
   * @param message 自定义消息
   */
  public WorkflowException(WorkflowExceptionCode exceptionCode, String message) {
    super(exceptionCode);
    setMessage(message);
  }

  /**
   * 使用通用异常码和自定义消息构造异常
   *
   * <p>保留用于需要携带通用异常码（如安全模块异常码）并补充自定义消息的场景。
   *
   * @param exceptionCode 通用异常码
   * @param message 自定义消息
   */
  protected WorkflowException(ExceptionCode exceptionCode, String message) {
    super(exceptionCode);
    setMessage(message);
  }

  /**
   * 使用通用异常码和原始异常构造异常
   *
   * @param exceptionCode 通用异常码
   * @param cause 原始异常
   */
  protected WorkflowException(ExceptionCode exceptionCode, Throwable cause) {
    super(exceptionCode, cause);
  }
}
