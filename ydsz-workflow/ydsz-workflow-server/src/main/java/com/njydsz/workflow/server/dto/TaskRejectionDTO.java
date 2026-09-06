package com.njydsz.workflow.server.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 任务批量驳回 DTO
 *
 * <p>用于批量驳回时的参数传递，包含任务 ID、驳回原因和可选的驳回目标节点。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class TaskRejectionDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID */
  private String taskId;

  /** 驳回原因 */
  private String reason;

  /** 驳回目标节点编码（null 则按流程默认） */
  private String targetNodeCode;
}
