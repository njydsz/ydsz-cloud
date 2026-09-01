package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * JobDagVersion 视图对象。
 *
 * <p>用于 Controller 层返回 DAG 版本历史数据，对应实体 {@link com.njydsz.cronjob.domain.entity.dag.JobDagVersion}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobDagVersionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** DAG ID（关联 ydsz_job_dag.id） */
  private String dagId;

  /** DAG KEY（冗余） */
  private String dagKey;

  /** 版本号（从 1 递增） */
  private Integer version;

  /** DAG 定义 JSON 快照 */
  private String dagDefinition;

  /** DAG 名称快照 */
  private String dagName;

  /** 触发类型快照 */
  private String triggerType;

  /** Cron 表达式快照 */
  private String cronExpression;

  /** 失败策略快照 */
  private String failStrategy;

  /** 版本备注（如"新增节点A"、"修改条件分支"） */
  private String remark;

  /** 变更操作人 */
  private String changedBy;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
