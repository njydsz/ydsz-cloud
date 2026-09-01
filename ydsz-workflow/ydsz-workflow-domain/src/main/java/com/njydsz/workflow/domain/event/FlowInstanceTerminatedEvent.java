package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程实例已被终止领域事件。
 *
 * <p>当流程实例被管理员或系统终止（状态变为 TERMINATED）时发布。
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>领域事件置于 {@code domain/event/} 包下、
 * 以 {@code Event} 结尾（符合 §34.2.1）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowInstanceTerminatedEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  private final String instanceId;
  private final String flowCode;
  private final String flowName;
  private final String businessType;
  private final String businessId;
  private final String initiatorId;
  private final String reason;

  public FlowInstanceTerminatedEvent(
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
