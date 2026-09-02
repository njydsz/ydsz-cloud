package com.njydsz.workflow.domain.event;

import java.io.Serial;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import static java.util.Collections.emptyMap;

/**
 * 流程实例已启动领域事件。
 *
 * <p>当流程实例成功创建并启动时发布，业务方可监听此事件执行后续业务逻辑
 * （如更新业务单据状态、发送通知等）。
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>领域事件置于 {@code domain/event/} 包下、
 * 以 {@code Event} 结尾（符合 §34.2.1 表格：event/ 领域事件类）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowInstanceStartedEvent extends FlowDomainEvent {

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

  /** 流程变量 */
  private final Map<String, Object> variables;

  public FlowInstanceStartedEvent(
      Object source,
      String instanceId,
      String flowCode,
      String flowName,
      String businessType,
      String businessId,
      String initiatorId,
      Map<String, Object> variables) {
    super(source);
    this.instanceId = instanceId;
    this.flowCode = flowCode;
    this.flowName = flowName;
    this.businessType = businessType;
    this.businessId = businessId;
    this.initiatorId = initiatorId;
    this.variables = variables != null ? variables : emptyMap();
  }
}
