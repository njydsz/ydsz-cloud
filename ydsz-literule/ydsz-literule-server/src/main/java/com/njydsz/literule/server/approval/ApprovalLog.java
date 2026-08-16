package com.njydsz.literule.server.approval;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批日志（P1-3 多级审批流）
 *
 * <p>记录单次审批操作的全部信息，包括审批人、动作、意见、委托目标等。 一个 {@link ApprovalRecord} 包含多条 ApprovalLog，按时间顺序追加。
 *
 * <p>动作类型（{@link #action}）取值：
 *
 * <ul>
 *   <li>{@link #ACTION_APPROVE} - 审批通过
 *   <li>{@link #ACTION_REJECT} - 审批驳回
 *   <li>{@link #ACTION_DELEGATE} - 委托他人审批
 *   <li>{@link #ACTION_COMMENT} - 仅评论（不改变状态）
 *   <li>{@link #ACTION_SUBMIT} - 提交审核
 *   <li>{@link #ACTION_CANCEL} - 撤回审核
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalLog implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 动作常量：审批通过 */
  public static final String ACTION_APPROVE = "APPROVE";

  /** 动作常量：审批驳回 */
  public static final String ACTION_REJECT = "REJECT";

  /** 动作常量：委托他人审批 */
  public static final String ACTION_DELEGATE = "DELEGATE";

  /** 动作常量：仅评论 */
  public static final String ACTION_COMMENT = "COMMENT";

  /** 动作常量：提交审核 */
  public static final String ACTION_SUBMIT = "SUBMIT";

  /** 动作常量：撤回审核 */
  public static final String ACTION_CANCEL = "CANCEL";

  /** 级别 */
  private int level;

  /** 审批人（工号） */
  private String approver;

  /** 动作：APPROVE/REJECT/DELEGATE/COMMENT/SUBMIT/CANCEL */
  private String action;

  /** 审批意见 */
  private String comment;

  /** 委托给（DELEGATE 时被委托人工号） */
  private String delegatedTo;

  /** 操作时间 */
  private LocalDateTime timestamp;
}
