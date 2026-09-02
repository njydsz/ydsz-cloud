package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 流程审计轨迹视图对象。
 *
 * <p>记录流程实例中每个节点的审批操作历史，包含操作人、操作动作、时间等信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowAuditTrailVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 审计记录主键 ID */
  private String id;

  /** 流程实例 ID */
  private String instanceId;

  /** 任务 ID */
  private String taskId;

  /** 流程编码 */
  private String flowCode;

  /** 业务类型 */
  private String businessType;

  /** 业务主键 ID */
  private String businessId;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 操作动作（通过/驳回/撤回等） */
  private String action;

  /** 操作人 ID */
  private String operatorId;

  /** 操作人姓名 */
  private String operatorName;

  /** 目标处理人 ID */
  private String targetId;

  /** 审批意见 */
  private String comment;

  /** 操作时间 */
  private LocalDateTime operatedAt;
}
