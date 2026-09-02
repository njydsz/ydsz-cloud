package com.njydsz.workflow.domain.event;

import java.io.Serial;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程定义已部署领域事件。
 *
 * <p>当流程定义被部署（创建/更新）时发布，
 * 业务方可监听此事件执行后续逻辑（如清除缓存、通知订阅者、触发版本快照等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class FlowDefinitionDeployedEvent extends FlowDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程定义 ID */
  private final String definitionId;

  /** 流程编码 */
  private final String flowCode;

  /** 流程名称 */
  private final String flowName;

  /** 流程版本号 */
  private final String flowVersion;

  /** 部署操作类型（CREATE / UPDATE） */
  private final String deployType;

  public FlowDefinitionDeployedEvent(
      Object source,
      String definitionId,
      String flowCode,
      String flowName,
      String flowVersion,
      String deployType) {
    super(source);
    this.definitionId = definitionId;
    this.flowCode = flowCode;
    this.flowName = flowName;
    this.flowVersion = flowVersion;
    this.deployType = deployType;
  }
}
