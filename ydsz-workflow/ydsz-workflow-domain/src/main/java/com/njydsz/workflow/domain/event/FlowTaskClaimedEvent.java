package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 任务已签收领域事件。
 *
 * <p>当任务被办理人签收时发布（PENDING → CLAIMED），
 * 业务方可监听此事件执行后续逻辑（如更新办理时效统计、通知委托人等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowTaskClaimedEvent extends FlowDomainEvent {

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

  /** 任务标题 */
  private final String title;

  public FlowTaskClaimedEvent(
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
