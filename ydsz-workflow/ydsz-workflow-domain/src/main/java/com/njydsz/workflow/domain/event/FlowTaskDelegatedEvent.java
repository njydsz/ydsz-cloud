package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 任务已委派领域事件。
 *
 * <p>当任务被委派给他人处理时发布（PENDING/CLAIMED → DELEGATED），
 * 业务方可监听此事件执行后续逻辑（如通知被委派人、记录委派轨迹等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowTaskDelegatedEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID */
  private final String taskId;

  /** 流程实例 ID */
  private final String instanceId;

  /** 节点编码 */
  private final String nodeCode;

  /** 节点名称 */
  private final String nodeName;

  /** 原办理人 ID */
  private final String fromAssigneeId;

  /** 被委派人 ID */
  private final String toAssigneeId;

  /** 委派原因 */
  private final String reason;

  public FlowTaskDelegatedEvent(
      Object source,
      String taskId,
      String instanceId,
      String nodeCode,
      String nodeName,
      String fromAssigneeId,
      String toAssigneeId,
      String reason) {
    super(source);
    this.taskId = taskId;
    this.instanceId = instanceId;
    this.nodeCode = nodeCode;
    this.nodeName = nodeName;
    this.fromAssigneeId = fromAssigneeId;
    this.toAssigneeId = toAssigneeId;
    this.reason = reason;
  }
}
