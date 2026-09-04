package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobDailyStatsRepository;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.log.JobDailyStatsMapper;

/**
 * 每日统计 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobDailyStatsRepository} 接口，封装 JobDailyStatsMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class JobDailyStatsRepositoryImpl implements JobDailyStatsRepository {

  /** 结束时间：小时（23 点） */
  private static final int END_HOUR = 23;

  /** 结束时间：分钟（59 分） */
  private static final int END_MINUTE = 59;

  /** 结束时间：秒（59 秒） */
  private static final int END_SECOND = 59;


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
      String jobId, LocalDate startDate, LocalDate endDate) {
    LocalDateTime start = startDate.atStartOfDay();
    LocalDateTime end = endDate.atTime(END_HOUR, END_MINUTE, END_SECOND);
    return findByJobIdAndDateRange(jobId, start, end);
  }
}
