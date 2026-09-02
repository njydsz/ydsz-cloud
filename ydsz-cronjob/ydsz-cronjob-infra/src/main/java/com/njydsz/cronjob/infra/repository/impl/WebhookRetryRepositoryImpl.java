package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.WebhookRetryRepository;
import com.njydsz.cronjob.domain.vo.JobWebhookRetryVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.job.JobWebhookRetry;
import com.njydsz.cronjob.infra.mapper.job.JobWebhookRetryMapper;

/**
 * WebHook 重试补偿 Repository 实现（Infra 层，P1-3 Webhook 投递保障）。
 *
 * <p>实现 {@link WebhookRetryRepository} 接口，封装 JobWebhookRetryMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class WebhookRetryRepositoryImpl implements WebhookRetryRepository {

  private final JobWebhookRetryMapper webhookRetryMapper;
  private final CronjobConverter converter;

  @Override
  public String create(JobWebhookRetryVO vo) {
    JobWebhookRetry entity = converter.jobWebhookRetryVOToEntity(vo);
    webhookRetryMapper.insertRetry(entity);
    return entity.getId();
  }

  @Override
  public List<JobWebhookRetryVO> findPendingRetries(LocalDateTime now, int limit) {
    return converter.jobWebhookRetryListToVO(webhookRetryMapper.selectPendingRetries(now, limit));
  }

  @Override
  public void updateForRetry(String id, int retryCount, LocalDateTime nextRetryTime, String lastError) {
    webhookRetryMapper.updateForRetry(id, retryCount, nextRetryTime, lastError != null ? lastError : "");
  }

  @Override
  public void markSuccess(String id, LocalDateTime successTime) {
    webhookRetryMapper.markSuccess(id, successTime);
  }

  @Override
  public void markDead(String id, LocalDateTime deadTime, String reason) {
    webhookRetryMapper.markDead(id, deadTime, reason != null ? reason : "");
  }

  @Override
  public long countPending() {
    return webhookRetryMapper.countPending();
  }

  @Override
  public long countDead() {
    return webhookRetryMapper.countDead();
  }
}
