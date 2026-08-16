package com.njydsz.message.domain.dto.core;

import java.util.List;

import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 消息编排流程 DTO。
 *
 * <p>P1-9: 定义一个完整的 DAG 消息编排流程，包含多个节点和依赖关系。 引擎按拓扑序执行各节点，支持条件分支和失败策略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class OrchestrationFlowDTO {

  /** 流程 ID */
  @Xss private String flowId;

  /** 流程名称 */
  @Xss private String flowName;

  /** 业务类型 */
  @Xss private String bizType;

  /** 业务单据 ID */
  @Xss private String bizId;

  /** 触发用户 ID */
  @Xss private String senderId;

  /** 节点列表 */
  private List<OrchestrationNodeDTO> nodes;

  /** 流程级超时（秒） */
  private Integer globalTimeoutSeconds = 300;
}
