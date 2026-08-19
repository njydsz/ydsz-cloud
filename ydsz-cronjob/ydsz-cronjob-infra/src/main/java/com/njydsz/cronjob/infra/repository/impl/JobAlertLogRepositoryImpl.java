package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.infra.entity.job.JobAlertLog;
import com.njydsz.cronjob.domain.repository.JobAlertLogRepository;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobAlertLogMapper;

/**
 * 告警日志 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobAlertLogRepository} 接口，封装 JobAlertLogMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobAlertLogRepositoryImpl implements JobAlertLogRepository {

  private final JobAlertLogMapper jobAlertLogMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobAlertLogVO> findByRuleIdSince(String ruleId, LocalDateTime since) {
    return converter.jobAlertLogListToVO(jobAlertLogMapper.selectByRuleIdSince(ruleId, since));
  }

  @Override
  public List<JobAlertLogVO> findByJobIdSince(String jobId, LocalDateTime since) {
    return converter.jobAlertLogListToVO(jobAlertLogMapper.selectByJobIdSince(jobId, since));
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobAlertLogMapper.cleanExpiredLogs(before, limit);
  }

  @Override
  public int insert(JobAlertLog alertLog) {
    return jobAlertLogMapper.insert(alertLog);
  }
}
