package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * JobDagNodeInstance 视图对象。
 *
 * <p>用于 Controller 层返回 DAG 节点实例数据，对应实体 {@link
 * com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobDagNodeInstanceVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** DAG 实例 ID */
  private String dagInstanceId;

  /** DAG 定义 ID */
  private String dagId;

  /** 任务 ID */
  private String jobId;

  /** 任务 KEY（冗余） */
  private String jobKey;

  /** 节点状态: PENDING / RUNNING / SUCCESS / FAILED / SKIPPED / RETRYING */
  private String nodeStatus;

  /** 关联任务执行日志 ID（ydsz_job_log.id） */
  private String logId;

  /** 节点级重试次数 */
  private Integer retryCount;

  /** 节点级最大重试次数 */
  private Integer maxRetries;

  /** 节点开始时间 */
  private LocalDateTime startedAt;

  /** 节点结束时间 */
  private LocalDateTime finishedAt;

  /** 节点执行耗时（毫秒） */
  private Long durationMs;

  /** 节点执行结果 JSON */
  private String resultJson;

  /** 节点错误信息 */
  private String errorMessage;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 租户 ID（多租户隔离） */
  private String tenantId;
}
