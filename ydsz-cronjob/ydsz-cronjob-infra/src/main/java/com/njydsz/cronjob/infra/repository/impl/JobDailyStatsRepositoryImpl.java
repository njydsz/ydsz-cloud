package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobDailyStatsRepository;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.log.JobDailyStatsMapper;

/**
 * 每日统计 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobDailyStatsRepository} 接口，封装 JobDailyStatsMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDailyStatsRepositoryImpl implements JobDailyStatsRepository {

  private final JobDailyStatsMapper jobDailyStatsMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobDailyStatsVO> findByJobIdAndDateRange(String jobId, LocalDateTime start, LocalDateTime end) {
    // P0-FIX: Mapper 按 LocalDate 统计（daily 粒度），原实现直接传 LocalDateTime 类型不匹配
    return converter.jobDailyStatsListToVO(
        jobDailyStatsMapper.selectByJobIdAndDateRange(jobId, start.toLocalDate(), end.toLocalDate()));
  }

  @Override
  public List<JobDailyStatsVO> findByJobKeyAndDateRange(String jobKey, LocalDateTime start, LocalDateTime end) {
    return converter.jobDailyStatsListToVO(
        jobDailyStatsMapper.selectByJobKeyAndDateRange(jobKey, start.toLocalDate(), end.toLocalDate()));
  }

  @Override
  public List<Map<String, Object>> aggregateDaily(LocalDateTime start, LocalDateTime end) {
    return jobDailyStatsMapper.aggregateDaily(start, end);
  }

  @Override
  public void upsert(JobDailyStatsVO vo) {
    jobDailyStatsMapper.upsert(converter.voToEntity(vo));
  }

  // ===== Web 层查询方法实现 =====

  @Override
  public List<JobDailyStatsVO> findByJobIdAndDateRange(
      String jobId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
    LocalDateTime start = startDate.atStartOfDay();
    LocalDateTime end = endDate.atTime(23, 59, 59);
    return findByJobIdAndDateRange(jobId, start, end);
  }
}
