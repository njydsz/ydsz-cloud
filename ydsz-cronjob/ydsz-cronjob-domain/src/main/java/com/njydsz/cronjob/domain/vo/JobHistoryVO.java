package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * JobHistory 视图对象。
 *
 * <p>用于 Controller 层返回任务配置历史版本数据，对应实体 {@link com.njydsz.cronjob.domain.entity.job.JobHistory}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobHistoryVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 任务 ID（关联 ydsz_job.id） */
  private String jobId;

  /** 版本号（对应更新前的 job.version） */
  private Integer version;

  /** 完整 Job JSON 快照（变更后状态; DELETE 时为 NULL） */
  private String snapshot;

  /** 变更类型: CREATE / UPDATE / DELETE */
  private String changeType;

  /** 变更前快照 JSON（CREATE 时为 NULL; UPDATE/DELETE 时记录变更前状态） */
  private String beforeSnapshot;

  /** 变更说明（如"任务创建"、"任务更新"、"任务删除"） */
  private String changeRemark;

  /** 任务名称（冗余） */
  private String jobName;

  /** 任务 KEY（冗余） */
  private String jobKey;

  /** 处理器（冗余） */
  private String handler;

  /** Cron 表达式（冗余） */
  private String cronExpression;

  /** 参数 JSON（冗余） */
  private String paramsJson;

  /** 备注（冗余） */
  private String remark;

  /** 修改人 ID */
  private String changedBy;

  /** 修改时间 */
  private LocalDateTime changedAt;

  /** 逻辑删除标记: 0 未删除 / 1 已删除 */
  private Integer historyDeleted;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
