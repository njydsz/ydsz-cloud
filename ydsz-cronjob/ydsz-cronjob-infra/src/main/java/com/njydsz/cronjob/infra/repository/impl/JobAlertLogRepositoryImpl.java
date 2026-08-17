package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobAlertLog;
import com.njydsz.cronjob.infra.mapper.job.JobAlertLogMapper;
import com.njydsz.cronjob.infra.repository.JobAlertLogRepository;

/**
 * 告警日志 Repository 实现。
 *
 * <p>委托 {@link JobAlertLogMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobAlertLogRepositoryImpl implements JobAlertLogRepository {

  private final JobAlertLogMapper jobAlertLogMapper;

  @Override
  public List<JobAlertLog> selectByRuleIdSince(String ruleId, LocalDateTime since) {
    return jobAlertLogMapper.selectByRuleIdSince(ruleId, since);
  }

  @Override
  public List<JobAlertLog> selectByJobIdSince(String jobId, LocalDateTime since) {
    return jobAlertLogMapper.selectByJobIdSince(jobId, since);
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobAlertLogMapper.cleanExpiredLogs(before, limit);
  }
}
