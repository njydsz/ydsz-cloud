package com.njydsz.cronjob.domain.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;

/**
 * 每日统计 Repository（domain 层契约）。
 *
 * <p>定义任务执行每日统计数据的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobDailyStatsVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface JobDailyStatsRepository {

  /**
   * 根据任务 ID 和日期范围查询统计记录。
   *
   * @param jobId 任务 ID
   * @param start 起始时间
   * @param end 结束时间
   * @return 统计记录 VO 列表
   */
  List<JobDailyStatsVO> findByJobIdAndDateRange(String jobId, LocalDateTime start, LocalDateTime end);

  /**
   * 根据任务 KEY 和日期范围查询统计记录。
   *
   * @param jobKey 任务 KEY
   * @param start 起始时间
   * @param end 结束时间
   * @return 统计记录 VO 列表
   */
  List<JobDailyStatsVO> findByJobKeyAndDateRange(String jobKey, LocalDateTime start, LocalDateTime end);

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
   * @param vo 统计记录 VO
   */
  void upsert(JobDailyStatsVO vo);

  // ===== Web 层查询方法（Controller 停止 Mapper 直注） =====

  /**
   * 根据任务 ID 和日期范围查询统计记录（LocalDate 重载）。
   *
   * @param jobId 任务 ID
   * @param startDate 起始日期（含）
   * @param endDate 结束日期（含）
   * @return 统计记录 VO 列表
   */
  List<JobDailyStatsVO> findByJobIdAndDateRange(String jobId, LocalDate startDate, LocalDate endDate);
}
