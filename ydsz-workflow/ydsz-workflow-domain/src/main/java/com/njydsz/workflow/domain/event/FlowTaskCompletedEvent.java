package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 任务已完成领域事件。
 *
 * <p>当运行时任务被办理（通过/驳回/转办等）后变为历史任务时发布。
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>领域事件置于 {@code domain/event/} 包下、
 * 以 {@code Event} 结尾（符合 §34.2.1）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowTaskCompletedEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  private final String taskId;
  private final String instanceId;
  private final String nodeCode;
  private final String nodeName;
  private final String assigneeId;
  private final String action;
  private final String comment;

  public FlowTaskCompletedEvent(
      Object source,
      String taskId,
      String instanceId,
      String nodeCode,
      String nodeName,
      String assigneeId,
      String action,
      String comment) {
    super(source);
    this.taskId = taskId;
    this.instanceId = instanceId;
    this.nodeCode = nodeCode;
    this.nodeName = nodeName;
    this.assigneeId = assigneeId;
    this.action = action;
    this.comment = comment;
  }
}
