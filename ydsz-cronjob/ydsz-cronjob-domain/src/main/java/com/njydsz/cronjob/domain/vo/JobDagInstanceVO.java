package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * JobDagInstance 视图对象。
 *
 * <p>用于 Controller 层返回 DAG 工作流实例数据，对应实体 {@link
 * com.njydsz.cronjob.domain.entity.dag.JobDagInstance}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDagInstanceVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** DAG 定义 ID */
  private String dagId;

  /** DAG KEY（冗余） */
  private String dagKey;

  /** 实例状态: PENDING / RUNNING / SUCCESS / FAILED / PARTIAL_SUCCESS / PAUSED / CANCELED */
  private String instanceStatus;

  /** 触发类型: MANUAL / CRON / DEPENDENT */
  private String triggerType;

  /** 触发人（MANUAL 时为用户 ID） */
  private String triggerBy;

  /** 触发 traceId（链路追踪） */
  private String triggerTraceId;

  /** DAG 实例级上下文 JSON（跨节点传参） */
  private String contextJson;

  /** 开始时间 */
  private LocalDateTime startedAt;

  /** 结束时间 */
  private LocalDateTime finishedAt;

  /** 执行耗时（毫秒） */
  private Long durationMs;

  /** 错误信息（FAILED 时填充） */
  private String errorMessage;

  /** 总节点数 */
  private Integer totalNodes;

  /** 成功节点数 */
  private Integer successNodes;

  /** 失败节点数 */
  private Integer failedNodes;

  /** 跳过节点数 */
  private Integer skippedNodes;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
