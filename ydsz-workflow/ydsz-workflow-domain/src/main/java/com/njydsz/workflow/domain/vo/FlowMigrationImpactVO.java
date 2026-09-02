package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 流程迁移影响分析视图对象。
 *
 * <p>用于评估流程定义迁移对正在运行实例的影响，提供风险等级和建议。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowMigrationImpactVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 原流程定义 ID */
  private String oldDefinitionId;

  /** 目标流程定义 ID */
  private String newDefinitionId;

  /** 风险等级：HIGH / MEDIUM / LOW / NONE */
  private String riskLevel;

  /** 正在运行的实例数量 */
  private Integer runningInstanceCount;

  /** 受影响的运行实例列表 */
  private List<Map<String, Object>> affectedInstances;

  /** 阻塞节点列表（迁移后无法继续执行的节点） */
  private List<Map<String, Object>> blockedNodes;

  /** 受影响的节点列表 */
  private List<Map<String, Object>> affectedNodes;

  /** 迁移建议 */
  private String recommendation;
}
