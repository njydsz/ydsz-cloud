package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 租户配额视图对象。
 *
 * <p>用于 Controller 层返回租户配额数据，对应实体 {@link com.njydsz.cronjob.domain.entity.job.TenantQuota}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class TenantQuotaVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 任务数上限（NULL=unlimited） */
  private Integer maxJobs;

  /** 并发执行上限（NULL=unlimited） */
  private Integer maxConcurrent;

  /** 日执行量上限（NULL=unlimited） */
  private Integer maxDailyExecutions;

  /** 是否启用配额检查: 0 禁用 / 1 启用 */
  private Integer enabled;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
