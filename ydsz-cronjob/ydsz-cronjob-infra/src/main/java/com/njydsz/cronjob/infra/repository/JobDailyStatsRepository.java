package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.entity.log.JobDailyStats;

/**
 * 每日统计 Repository。
 *
 * <p>封装 {@code ydsz_job_daily_stats} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDailyStatsRepository {

  /**
   * 根据任务 ID 和日期范围查询统计记录。
   *
   * @param jobId 任务 ID
   * @param start 起始时间
   * @param end 结束时间
   * @return 统计记录列表
   */
  List<JobDailyStats> selectByJobIdAndDateRange(String jobId, LocalDateTime start, LocalDateTime end);

  /**
   * 根据任务 KEY 和日期范围查询统计记录。
   *
   * @param jobKey 任务 KEY
   * @param start 起始时间
   * @param end 结束时间
   * @return 统计记录列表
   */
  List<JobDailyStats> selectByJobKeyAndDateRange(String jobKey, LocalDateTime start, LocalDateTime end);

  /**
   * 聚合指定时间窗口内的执行日志为每日统计。
   *
   * @param start 起始时间
   * @param end 结束时间
   * @return 聚合结果列表
   */
  List<Map<String, Object>> aggregateDaily(LocalDateTime start, LocalDateTime end);

  /**
   * 插入或更新每日统计记录（UPSERT）。
   *
   * @param stats 统计记录
   */
  void upsert(JobDailyStats stats);
}
