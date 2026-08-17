package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.infra.mapper.job.JobAlertRuleMapper;
import com.njydsz.cronjob.infra.repository.JobAlertRuleRepository;

/**
 * 告警规则 Repository 实现。
 *
 * <p>委托 {@link JobAlertRuleMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobAlertRuleRepositoryImpl implements JobAlertRuleRepository {

  private final JobAlertRuleMapper jobAlertRuleMapper;

  @Override
  public List<JobAlertRule> selectAllEnabled() {
    return jobAlertRuleMapper.selectAllEnabled();
  }

  @Override
  public List<JobAlertRule> selectByJobIdOrGlobal(String jobId) {
    return jobAlertRuleMapper.selectByJobIdOrGlobal(jobId);
  }

  @Override
  public List<JobAlertRule> selectByAlertType(String alertType) {
    return jobAlertRuleMapper.selectByAlertType(alertType);
  }

  @Override
  public List<JobAlertRule> selectSlaRulesByJobId(String jobId) {
    return jobAlertRuleMapper.selectSlaRulesByJobId(jobId);
  }

  @Override
  public int updateLastAlertAtIfNotInCooldown(String ruleId, LocalDateTime now, LocalDateTime cooldownBefore) {
    return jobAlertRuleMapper.updateLastAlertAtIfNotInCooldown(ruleId, now, cooldownBefore);
  }
}
