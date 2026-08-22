package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 任务已创建领域事件。
 *
 * <p>当新的运行时任务（待办）被创建时发布，业务方可监听此事件发送待办通知。
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>领域事件置于 {@code domain/event/} 包下、
 * 以 {@code Event} 结尾（符合 §34.2.1）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@ToString
public class FlowTaskCreatedEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  private final String taskId;
  private final String instanceId;
  private final String nodeCode;
  private final String nodeName;
  private final String assigneeId;
  private final String title;

  public FlowTaskCreatedEvent(
      Object source,
      String taskId,
      String instanceId,
      String nodeCode,
      String nodeName,
      String assigneeId,
      String title) {
    super(source);
    this.taskId = taskId;
    this.instanceId = instanceId;
    this.nodeCode = nodeCode;
    this.nodeName = nodeName;
    this.assigneeId = assigneeId;
    this.title = title;
  }
}
