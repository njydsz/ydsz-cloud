package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobAlertRuleRepository;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobAlertRuleMapper;

/**
 * 告警规则 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobAlertRuleRepository} 接口，封装 JobAlertRuleMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobAlertRuleRepositoryImpl implements JobAlertRuleRepository {

  private final JobAlertRuleMapper jobAlertRuleMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobAlertRuleVO> findAllEnabled() {
    return converter.jobAlertRuleListToVO(jobAlertRuleMapper.selectAllEnabled());
  }

  @Override
  public List<JobAlertRuleVO> findByJobIdOrGlobal(String jobId) {
    return converter.jobAlertRuleListToVO(jobAlertRuleMapper.selectByJobIdOrGlobal(jobId));
  }

  @Override
  public List<JobAlertRuleVO> findByAlertType(String alertType) {
    return converter.jobAlertRuleListToVO(jobAlertRuleMapper.selectByAlertType(alertType));
  }

  @Override
  public List<JobAlertRuleVO> findSlaRulesByJobId(String jobId) {
    return converter.jobAlertRuleListToVO(jobAlertRuleMapper.selectSlaRulesByJobId(jobId));
  }

  @Override
  public int updateLastAlertAtIfNotInCooldown(String ruleId, LocalDateTime now, LocalDateTime cooldownBefore) {
    return jobAlertRuleMapper.updateLastAlertAtIfNotInCooldown(ruleId, now, cooldownBefore);
  }
}
