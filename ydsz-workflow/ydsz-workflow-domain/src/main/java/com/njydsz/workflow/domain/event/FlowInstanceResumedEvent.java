package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程实例已恢复领域事件。
 *
 * <p>当流程实例从挂起状态恢复为运行时发布（SUSPENDED → RUNNING），
 * 业务方可监听此事件执行后续逻辑（如通知办理人流程恢复、恢复 SLA 计时等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@ToString
public class FlowInstanceResumedEvent extends FlowDomainEvent {

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

  public FlowInstanceResumedEvent(
      Object source,
      String instanceId,
      String flowCode,
      String flowName,
      String businessType,
      String businessId,
      String initiatorId) {
    super(source);
    this.instanceId = instanceId;
    this.flowCode = flowCode;
    this.flowName = flowName;
    this.businessType = businessType;
    this.businessId = businessId;
    this.initiatorId = initiatorId;
  }
}
