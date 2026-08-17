package com.njydsz.cronjob.infra.entity.log;

import java.io.Serial;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 任务执行每日统计实体（P2-3 执行历史趋势可视化）。
 *
 * <p>对应 {@code ydsz_job_daily_stats} 表，每天凌晨由 {@code DailyStatsAggregator} 聚合 {@code ydsz_job_log}
 * 的执行数据，供前端趋势图展示（成功率/耗时折线图）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_daily_stats")
public class JobDailyStatsDO extends MpBaseIdEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID */
  private String jobId;

  /** 任务 KEY（冗余） */
  private String jobKey;

  /** 统计日期 */
  private LocalDate statsDate;

  /** 当日触发次数 */
  private Long fireCount;

  /** 当日成功次数 */
  private Long successCount;

  /** 当日失败次数 */
  private Long failCount;

  /** 当日超时次数 */
  private Long timeoutCount;

  /** 平均耗时（毫秒） */
  private Long avgDurationMs;

  /** 最大耗时（毫秒） */
  private Long maxDurationMs;

  /** 最小耗时（毫秒） */
  private Long minDurationMs;

  /** P95 耗时（毫秒） */
  private Long p95DurationMs;
}
