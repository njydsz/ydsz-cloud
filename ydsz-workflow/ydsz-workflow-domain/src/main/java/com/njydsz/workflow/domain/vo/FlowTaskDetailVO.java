package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 流程任务详情视图对象。
 *
 * <p>包含任务的基本信息、处理人、状态和时间等完整详情，用于任务详情页展示。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowTaskDetailVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务主键 ID */
  private String id;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 节点类型 */
  private String nodeType;

  /** 处理人类型（如用户/角色等） */
  private String assigneeType;

  /** 处理人 ID */
  private String assigneeId;

  /** 处理人姓名 */
  private String assigneeName;

  /** 执行类型（如会签/或签等） */
  private String performType;

  /** 任务状态 */
  private String taskStatus;

  /** 审批意见 */
  private String comment;

  /** 创建时间 */
  private LocalDateTime createAt;

  /** 认领时间 */
  private LocalDateTime claimAt;

  /** 完成时间 */
  private LocalDateTime finishAt;

  /** 任务耗时（毫秒） */
  private Long durationMs;

  /** 截止时间 */
  private LocalDateTime dueAt;
}
