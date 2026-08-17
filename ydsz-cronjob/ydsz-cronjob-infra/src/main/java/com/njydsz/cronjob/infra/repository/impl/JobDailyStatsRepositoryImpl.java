package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.log.JobDailyStats;
import com.njydsz.cronjob.infra.mapper.log.JobDailyStatsMapper;
import com.njydsz.cronjob.infra.repository.JobDailyStatsRepository;

/**
 * 每日统计 Repository 实现。
 *
 * <p>委托 {@link JobDailyStatsMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDailyStatsRepositoryImpl implements JobDailyStatsRepository {

  private final JobDailyStatsMapper jobDailyStatsMapper;

  @Override
  public List<JobDailyStats> selectByJobIdAndDateRange(String jobId, LocalDateTime start, LocalDateTime end) {
    return jobDailyStatsMapper.selectByJobIdAndDateRange(jobId, start, end);
  }

  @Override
  public List<JobDailyStats> selectByJobKeyAndDateRange(String jobKey, LocalDateTime start, LocalDateTime end) {
    return jobDailyStatsMapper.selectByJobKeyAndDateRange(jobKey, start, end);
  }

  @Override
  public List<Map<String, Object>> aggregateDaily(LocalDateTime start, LocalDateTime end) {
    return jobDailyStatsMapper.aggregateDaily(start, end);
  }

  @Override
  public void upsert(JobDailyStats stats) {
    jobDailyStatsMapper.upsert(stats);
  }
}
