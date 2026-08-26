package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 租户级配额配置（P7-2）。
 *
 * <p>控制单个租户可创建的任务数、并发执行数、日执行总量，防止 noisy neighbor 问题。 默认禁用（{@link #isEnabled} = false），启用后：
 *
 * <ul>
 *   <li>任务创建时检查 maxJobs（DB 驱动，每租户独立配置）
 *   <li>任务派发时检查 maxConcurrent（Redis 实时计数器，P7-3 实现）
 *   <li>任务派发时检查 maxDailyExecutions（Redis 日计数器，P7-3 实现）
 * </ul>
 *
 * <p>未配置租户配额记录时（{@code ydsz_job_tenant_quota} 表无对应行），默认不限制（unlimited）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class QuotaConfig {

  /** 是否启用租户级配额检查（false=不检查，所有租户 unlimited） */
  private boolean enabled = false;

  /** 默认任务数上限（当租户未在 ydsz_job_tenant_quota 表配置时使用，null=unlimited） */
  private Integer defaultMaxJobs = null;

  /** 默认并发执行上限（当租户未配置时使用，null=unlimited） */
  private Integer defaultMaxConcurrent = null;

  /** 默认日执行量上限（当租户未配置时使用，null=unlimited） */
  private Integer defaultMaxDailyExecutions = null;
}
