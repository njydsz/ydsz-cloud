package com.njydsz.workflow.domain.enums;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 工作流模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}， 支持
 * i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 *
 * <ul>
 *   <li>B70001-B70099 流程模板/定义
 *   <li>B71001-B71099 流程实例
 *   <li>B72001-B72099 任务
 *   <li>B73001-B73099 委托授权
 *   <li>B74001-B74099 分类/评论/附件
 *   <li>B75001-B75099 SLA/催办
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "workflow", description = "工作流")
public enum WorkflowExceptionCode implements ExceptionCode {

  // ==================== B70001-B70099 流程模板/定义 ====================
  /** Template not found */
  TEMPLATE_NOT_FOUND("B70001", "workflow.template.not.found", 404),
  /** Template code duplicate */
  TEMPLATE_CODE_DUPLICATE("B70002", "workflow.template.code.duplicate"),
  /** Template deployed cannot delete */
  TEMPLATE_DEPLOYED_CANNOT_DELETE("B70003", "workflow.template.deployed.cannot.delete"),
  /** Definition not found */
  DEFINITION_NOT_FOUND("B70004", "workflow.definition.not.found", 404),
  /** Bpmn parse error */
  BPMN_PARSE_ERROR("B70005", "workflow.bpmn.parse.error"),

  // ==================== B71001-B71099 流程实例 ====================
  /** Instance not found */
  INSTANCE_NOT_FOUND("B71001", "workflow.instance.not.found", 404),
  /** Instance status invalid */
  INSTANCE_STATUS_INVALID("B71002", "workflow.instance.status.invalid"),
  /** Instance already finished */
  INSTANCE_ALREADY_FINISHED("B71003", "workflow.instance.already.finished"),

  // ==================== B72001-B72099 任务 ====================
  /** Task not found */
  TASK_NOT_FOUND("B72001", "workflow.task.not.found", 404),
  /** Task no permission */
  TASK_NO_PERMISSION("B72002", "workflow.task.no.permission", 403),
  /** Task already handled */
  TASK_ALREADY_HANDLED("B72003", "workflow.task.already.handled"),
  /** Task approver duplicate */
  TASK_APPROVER_DUPLICATE("B72004", "workflow.task.approver.duplicate"),
  /** Illegal state transition */
  ILLEGAL_STATE_TRANSITION("B72005", "workflow.task.illegal.state.transition"),

  // ==================== B73001-B73099 委托授权 ====================
  /** Delegate auth not found */
  DELEGATE_AUTH_NOT_FOUND("B73001", "workflow.delegate.auth.not.found", 404),
  /** Delegate auth expired */
  DELEGATE_AUTH_EXPIRED("B73002", "workflow.delegate.auth.expired"),

  // ==================== B74001-B74099 分类/评论/附件 ====================
  /** Category not found */
  CATEGORY_NOT_FOUND("B74001", "workflow.category.not.found", 404),
  /** Category code duplicate */
  CATEGORY_CODE_DUPLICATE("B74002", "workflow.category.code.duplicate"),
  /** Comment not found */
  COMMENT_NOT_FOUND("B74003", "workflow.comment.not.found", 404),
  /** Attachment not found */
  ATTACHMENT_NOT_FOUND("B74004", "workflow.attachment.not.found", 404),

  // ==================== B75001-B75099 SLA/催办 ====================
  /** Sla not found */
  SLA_NOT_FOUND("B75001", "workflow.sla.not.found", 404),
  /** Sla overdue */
  SLA_OVERDUE("B75002", "workflow.sla.overdue"),
  /** Urge too frequent */
  URGE_TOO_FREQUENT("B75003", "workflow.urge.too.frequent", 429);

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** 默认 HTTP 状态码：参数错误 */
  private static final int DEFAULT_HTTP_STATUS = 400;

  /** HTTP 状态码 */
  private final int httpStatus;

  WorkflowExceptionCode(String code, String key) {
    this(code, key, DEFAULT_HTTP_STATUS);
  }

  WorkflowExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
