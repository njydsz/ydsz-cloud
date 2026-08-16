package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * JobDailyStats 视图对象。
 *
 * <p>用于 Controller 层返回任务每日统计趋势数据，对应实体 {@link com.njydsz.cronjob.domain.entity.log.JobDailyStats}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDailyStatsVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

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

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
