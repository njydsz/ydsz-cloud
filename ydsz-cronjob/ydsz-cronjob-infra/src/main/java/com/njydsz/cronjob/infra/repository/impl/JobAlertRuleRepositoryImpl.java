package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.repository.JobAlertRuleRepository;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.infra.mapper.job.JobAlertRuleMapper;

/**
 * 告警规则 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 26.09.01
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
  public Optional<JobAlertRuleVO> findById(String id) {
    return Optional.ofNullable(jobAlertRuleMapper.selectById(id))
        .map(converter::entityToVO);
  }

  @Override
  public int updateLastAlertAtIfNotInCooldown(String ruleId, LocalDateTime now, LocalDateTime cooldownBefore) {
    return jobAlertRuleMapper.updateLastAlertAtIfNotInCooldown(ruleId, now, cooldownBefore);
  }

  @Override
  public String insert(AlertRuleSaveDTO dto) {
    JobAlertRule entity = converter.dtoToEntity(dto);
    jobAlertRuleMapper.insert(entity);
    return entity.getId();
  }

  @Override
  public int update(AlertRuleSaveDTO dto) {
    JobAlertRule entity = converter.dtoToEntity(dto);
    return jobAlertRuleMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return jobAlertRuleMapper.deleteById(id);
  }
}
