package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobWebhookRepository;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobWebhookMapper;

/**
 * 任务 Webhook Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobWebhookRepository} 接口，封装 JobWebhookMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobWebhookRepositoryImpl implements JobWebhookRepository {

  private final JobWebhookMapper jobWebhookMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobWebhookVO> findActiveByEventType(String eventType) {
    return converter.jobWebhookListToVO(jobWebhookMapper.selectActiveByEventType(eventType));
  }

  @Override
  public List<JobWebhookVO> findActiveByEventAndJob(String eventType, String jobId) {
    return converter.jobWebhookListToVO(jobWebhookMapper.selectActiveByEventAndJob(eventType, jobId));
  }
}
