package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程实例已回滚领域事件。
 *
 * <p>当已完成的流程实例被撤销时发布（COMPLETED → ROLLED_BACK），
 * 业务方可监听此事件执行回滚补偿逻辑（如恢复业务单据状态、清理下游数据等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowInstanceRolledBackEvent extends FlowDomainEvent {

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

  /** 回滚原因 */
  private final String reason;

  public FlowInstanceRolledBackEvent(
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
