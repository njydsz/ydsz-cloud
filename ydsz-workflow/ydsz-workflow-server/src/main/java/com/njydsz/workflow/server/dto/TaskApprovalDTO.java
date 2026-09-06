package com.njydsz.workflow.server.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 任务批量通过 DTO
 *
 * <p>用于批量审批通过时的参数传递，包含任务 ID 和可选的审批意见。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TaskApprovalDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID */
  private String taskId;

  /** 审批意见 */
  private String comment;

  /** 流程变量（可选） */
  private String variables;
}
