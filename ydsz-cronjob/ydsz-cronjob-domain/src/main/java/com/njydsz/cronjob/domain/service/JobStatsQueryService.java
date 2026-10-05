package com.njydsz.cronjob.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * 任务执行统计查询 Service 接口（domain 层）。
 *
 * <p>封装 JobStatsController 所需的每日统计、汇总、热力图等查询能力，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface JobStatsQueryService {

  /**
   * 查询指定任务的每日统计（趋势图数据源）。
   *
   * @param jobId 任务 ID
   * @param startDate 起始日期（含）
   * @param endDate 结束日期（含）
   * @return 每日统计列表（按日期升序）
   */
  List<JobDailyStatsVO> getDailyStats(String jobId, LocalDate startDate, LocalDate endDate);

  /**
   * 查询指定任务在日期范围内的汇总统计。
   *
   * @param jobId 任务 ID
   * @param startDate 起始日期（含）
   * @param endDate 结束日期（含）
   * @return 汇总统计 Map
   */
  Map<String, Object> getSummary(String jobId, LocalDate startDate, LocalDate endDate);

  /**
   * 获取全局监控仪表盘数据。
   *
   * @return 仪表盘数据
   */
  Map<String, Object> getDashboard();

  /**
   * 获取最近失败任务列表。
   *
   * @param limit 返回条数
   * @return 失败日志列表
   */
  List<JobLogVO> getRecentFailures(int limit);

  /**
   * 获取任务执行热力图数据（按小时分布）。
   *
   * @param date 查询日期
   * @return 24 小时执行分布
   */
  List<Map<String, Object>> getHeatmap(LocalDate date);
}
