package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程实例已挂起领域事件。
 *
 * <p>当流程实例被管理员或系统暂停时发布（RUNNING/SUSPENDED → SUSPENDED），
 * 业务方可监听此事件执行后续逻辑（如通知办理人流程暂停、暂停 SLA 计时等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowInstanceSuspendedEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程实例 ID */
  private final String instanceId;

  /** 流程编码 */
  private final String flowCode;

  /** 流程名称 */
  private final String flowName;

  /** 业务类型 */
  private final String businessType;

  /** 业务单据 ID */
  private final String businessId;

  /** 发起人 ID */
  private final String initiatorId;

  /** 挂起原因 */
  private final String reason;

  public FlowInstanceSuspendedEvent(
      Object source,
      String instanceId,
      String flowCode,
      String flowName,
      String businessType,
      String businessId,
      String initiatorId,
      String reason) {
    super(source);
    this.instanceId = instanceId;
    this.flowCode = flowCode;
    this.flowName = flowName;
    this.businessType = businessType;
    this.businessId = businessId;
    this.initiatorId = initiatorId;
    this.reason = reason;
  }
}
