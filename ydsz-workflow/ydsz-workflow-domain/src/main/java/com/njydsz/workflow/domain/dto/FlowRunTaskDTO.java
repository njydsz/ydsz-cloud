package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 运行时任务命令 DTO（CUD 操作入参）。
 *
 * <p>用于 FlowRunTaskRepository 的 save/update 方法入参，
 * 符合 §34.2.1（dto/ 命令请求参数 以 DTO 结尾）。
 *
 * <p><b>命名合规说明（v2.23 DDD 分层规范）：</b>CUD 入参必须是 dto/ 下的 DTO 对象，
 * 禁止使用 VO（符合 §34.2.1）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowRunTaskDTO {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID（更新时必填） */
  private String id;

  /** 流程实例 ID */
  private String instanceId;

  /** 流程定义 ID */
  private String definitionId;

  /** 流程编码 */
  private String flowCode;

  /** 流程名称 */
  private String flowName;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 任务标题 */
  private String title;

  /** 办理人 ID */
  private String assigneeId;

  /** 办理人名称 */
  private String assigneeName;

  /** 任务状态（PENDING / CLAIMED / PASSED / REJECTED 等） */
  private String taskStatus;

  /** 业务类型 */
  private String businessType;

  /** 业务单据 ID */
  private String businessId;

  /** 业务单据编号 */
  private String businessNo;

  /** 租户 ID */
  private String tenantId;

  /** 优先级 */
  private Integer priority;

  /** 任务意见/备注 */
  private String comment;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 完成时间 */
  private LocalDateTime finishAt;

  /** 截止时间 */
  private LocalDateTime dueAt;

  /** 耗时（毫秒） */
  private Long durationMs;

  /** 删除标记（0=未删除，1=已删除） */
  private Integer deleted;
}
