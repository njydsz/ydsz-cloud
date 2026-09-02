package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 流程时间线视图对象。
 *
 * <p>聚合流程实例的历史任务、审计日志和当前任务，按时间顺序展示流程执行全过程。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowTimelineVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 事件类型：HIS_TASK（历史任务）/ AUDIT_LOG（审计日志）/ CURRENT_TASK（当前任务） */
  private String type;

  /** 事件发生时间戳 */
  private LocalDateTime timestamp;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 处理人 ID */
  private String assigneeId;

  /** 处理人姓名 */
  private String assigneeName;

  /** 操作动作 */
  private String action;

  /** 审批意见 */
  private String comment;

  /** 任务状态 */
  private String taskStatus;
}
