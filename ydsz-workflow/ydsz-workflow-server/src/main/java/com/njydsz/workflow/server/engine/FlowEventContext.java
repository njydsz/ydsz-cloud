package com.njydsz.workflow.server.engine;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程事件上下文元数据
 *
 * <p>P2-37: 携带 operatorId/operatedAt/traceId/tenantId 等上下文信息， 供监听器获取完整的事件元数据，对标用友 BPM /
 * 审批事件通知能力。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowEventContext {
  /** 流程实例 ID */
  private String instanceId;

  /** 任务 ID */
  private String taskId;

  /** 节点编码（事件触发节点） */
  private String nodeCode;

  /** 操作人 ID */
  private String operatorId;

  /** 操作动作（PASS/REJECT/TERMINATE/SUSPEND 等） */
  private String action;

  /** 租户 ID */
  private String tenantId;

  /** 链路追踪 ID */
  private String traceId;

  /** 操作时间 */
  private LocalDateTime operatedAt;

  /** 当前会签已通过人数（含本次），仅 TASK_PERSONAL_FINISHED 事件填充 */
  private Integer approveFinished;

  /** 会签总人数（需要多少人通过才算完成），仅 TASK_PERSONAL_FINISHED 事件填充 */
  private Integer approveCount;
}
