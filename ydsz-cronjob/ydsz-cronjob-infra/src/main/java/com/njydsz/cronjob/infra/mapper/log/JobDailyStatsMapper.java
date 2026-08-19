package com.njydsz.cronjob.infra.mapper.log;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.cronjob.domain.entity.log.JobDailyStats;

/**
 * 任务日统计 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_daily_stats</code>。
 *
 * <p>按任务×日维度固化统计结果，用于任务大盘、告警阈值、绩效考核。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_job_date — (任务+日期) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.entity.log.JobDailyStats 日统计实体
 * @see com.njydsz.cronjob.server.service.JobStatsService 任务统计 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobDailyStatsMapper extends BaseMapper<JobDailyStats> {

  /**
   * 查询指定任务在日期范围内的每日统计（按日期升序）。
   *
   * @param jobId 任务 ID
   * @param startDate 起始日期（含）
   * @param endDate 结束日期（含）
   * @return 每日统计列表（按 stats_date 升序）
   */
  @Select(
      "SELECT id, job_id, job_key, stats_date, fire_count, success_count, "
          + "fail_count, timeout_count, avg_duration_ms, max_duration_ms, "
          + "min_duration_ms, p95_duration_ms, created_at, deleted "
          + "FROM ydsz_job_daily_stats "
          + "WHERE job_id = #{jobId} AND deleted = 0 "
          + "AND stats_date >= #{startDate} AND stats_date <= #{endDate} "
          + "ORDER BY stats_date ASC")
  List<JobDailyStats> selectByJobIdAndDateRange(
      @Param("jobId") String jobId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * 查询指定任务 KEY 在日期范围内的每日统计（按日期升序）。
   *
   * <p>供无法获取 jobId 的场景使用（如仅持有 jobKey 的外部系统）。
   *
   * @param jobKey 任务 KEY
   * @param startDate 起始日期（含）
   * @param endDate 结束日期（含）
   * @return 每日统计列表（按 stats_date 升序）
   */
  @Select(
      "SELECT id, job_id, job_key, stats_date, fire_count, success_count, "
          + "fail_count, timeout_count, avg_duration_ms, max_duration_ms, "
          + "min_duration_ms, p95_duration_ms, created_at, deleted "
          + "FROM ydsz_job_daily_stats "
          + "WHERE job_key = #{jobKey} AND deleted = 0 "
          + "AND stats_date >= #{startDate} AND stats_date <= #{endDate} "
          + "ORDER BY stats_date ASC")
  List<JobDailyStats> selectByJobKeyAndDateRange(
      @Param("jobKey") String jobKey,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * 聚合 {@code ydsz_job_log} 在指定时间窗口内的执行统计（按 job_id 分组）。
   *
   * <p>由 {@code DailyStatsAggregator} 每天凌晨调用，聚合昨天的执行日志。 聚合字段：触发/成功/失败/超时次数 + avg/max/min/p95 耗时。
   *
   * <p>注意：{@code PERCENTILE_CONT} 是 PostgreSQL 标准聚合函数； {@code AVG} 返回 double，通过 {@code ::BIGINT} 转为
   * long。
   *
   * @param start 窗口起点（含）
   * @param end 窗口终点（不含）
   * @return 聚合结果列表，每条 Map 包含 job_id/job_key/fire_count/success_count/
   *     fail_count/timeout_count/avg_duration_ms/max_duration_ms/ min_duration_ms/p95_duration_ms
   *     字段
   */
  @Select(
      "SELECT job_id, job_key, "
          + "COUNT(1) as fire_count, "
          + "SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) as success_count, "
          + "SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) as fail_count, "
          + "SUM(CASE WHEN status='TIMEOUT' THEN 1 ELSE 0 END) as timeout_count, "
          + "AVG(duration_ms)::BIGINT as avg_duration_ms, "
          + "MAX(duration_ms) as max_duration_ms, "
          + "MIN(duration_ms) as min_duration_ms, "
          + "PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms)::BIGINT as p95_duration_ms "
          + "FROM ydsz_job_log "
          + "WHERE created_at >= #{start} AND created_at < #{end} AND deleted = 0 "
          + "GROUP BY job_id, job_key")
  List<Map<String, Object>> aggregateDaily(
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  /**
   * UPSERT 写入每日统计（PostgreSQL ON CONFLICT 语义）。
   *
   * <p>冲突键为 {@code (job_id, stats_date, deleted)}（对应唯一约束 {@code uk_pjds_job_date}）。
   * 冲突时更新统计字段，保证同一任务同一天仅保留最新聚合结果（如重跑聚合时覆盖旧值）。
   *
   * @param stats 统计实体
   * @return 受影响行数（1=插入或更新成功）
   */
  @Insert(
      "INSERT INTO ydsz_job_daily_stats (id, job_id, job_key, stats_date, "
          + "fire_count, success_count, fail_count, timeout_count, "
          + "avg_duration_ms, max_duration_ms, min_duration_ms, p95_duration_ms, "
          + "created_at, deleted) "
          + "VALUES (#{stats.id}, #{stats.jobId}, #{stats.jobKey}, #{stats.statsDate}, "
          + "#{stats.fireCount}, #{stats.successCount}, #{stats.failCount}, #{stats.timeoutCount}, "
          + "#{stats.avgDurationMs}, #{stats.maxDurationMs}, #{stats.minDurationMs}, #{stats.p95DurationMs}, "
          + "CURRENT_TIMESTAMP, 0) "
          + "ON CONFLICT (job_id, stats_date, deleted) DO UPDATE SET "
          + "fire_count = EXCLUDED.fire_count, "
          + "success_count = EXCLUDED.success_count, "
          + "fail_count = EXCLUDED.fail_count, "
          + "timeout_count = EXCLUDED.timeout_count, "
          + "avg_duration_ms = EXCLUDED.avg_duration_ms, "
          + "max_duration_ms = EXCLUDED.max_duration_ms, "
          + "min_duration_ms = EXCLUDED.min_duration_ms, "
          + "p95_duration_ms = EXCLUDED.p95_duration_ms")
  int upsert(@Param("stats") JobDailyStats stats);
}
