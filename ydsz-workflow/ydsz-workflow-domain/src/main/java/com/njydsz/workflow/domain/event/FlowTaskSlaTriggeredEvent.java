package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 任务 SLA 已触发领域事件。
 *
 * <p>当任务的 SLA 规则被触发时发布（如超时催办、自动升级、自动通过/驳回），
 * 业务方可监听此事件执行后续逻辑（如记录 SLA 执行历史、通知管理员等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowTaskSlaTriggeredEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID */
  private final String taskId;

  /** 流程实例 ID */
  private final String instanceId;

  /** 节点编码 */
  private final String nodeCode;

  /** 节点名称 */
  private final String nodeName;

  /** 办理人 ID */
  private final String assigneeId;

  /** SLA 动作（REMIND / ESCALATE / AUTO_PASS / AUTO_REJECT） */
  private final String slaAction;

  /** 截止时间 */
  private final java.time.LocalDateTime dueAt;

  public FlowTaskSlaTriggeredEvent(
      Object source,
      String taskId,
      String instanceId,
      String nodeCode,
      String nodeName,
      String assigneeId,
      String slaAction,
      java.time.LocalDateTime dueAt) {
    super(source);
    this.taskId = taskId;
    this.instanceId = instanceId;
    this.nodeCode = nodeCode;
    this.nodeName = nodeName;
    this.assigneeId = assigneeId;
    this.slaAction = slaAction;
    this.dueAt = dueAt;
  }
}
