package com.njydsz.workflow.domain.query;

import java.io.Serial;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 运行时任务复杂查询参数。
 *
 * <p>用于 FlowRunTaskRepository.findByCondition 方法的参数化查询，
 * 支持多条件组合过滤运行时任务列表。所有字段均为可选，为空时忽略该条件。
 *
 * <p><b>命名合规说明（26.09.01 DDD 分层规范）：</b>查询请求参数置于 {@code query/} 包下、以 {@code Query} 结尾
 * （符合 §34.2.1 表格：query/ 查询请求参数 以 Query 结尾）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowTaskQuery {

  @Serial private static final long serialVersionUID = 1L;

  /** 租户 ID */
  private String tenantId;

  /** 流程编码 */
  private String flowCode;

  /** 流程实例 ID */
  private String instanceId;

  /** 节点编码 */
  private String nodeCode;

  /** 办理人 ID */
  private String assigneeId;

  /** 任务状态（PENDING / CLAIMED / FROZEN 等） */
  private String taskStatus;

  /** 业务类型 */
  private String businessType;

  /** 业务单据 ID */
  private String businessId;

  /** 优先级（精确匹配） */
  private Integer priority;

  /** 创建时间起（范围查询） */
  private LocalDateTime createdAtFrom;

  /** 创建时间止（范围查询） */
  private LocalDateTime createdAtTo;

  /** 截止时间起（范围查询） */
  private LocalDateTime dueAtFrom;

  /** 截止时间止（范围查询） */
  private LocalDateTime dueAtTo;

  /** 排序字段（默认 createdAt） */
  private String orderBy;

  /** 排序方向（ASC / DESC，默认 DESC） */
  private String orderDirection;

  /** 偏移量 */
  private int offset;

  /** 每页大小 */
  private int limit;
}
