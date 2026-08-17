package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobWebhook;
import com.njydsz.cronjob.infra.mapper.job.JobWebhookMapper;
import com.njydsz.cronjob.infra.repository.JobWebhookRepository;

/**
 * 任务 Webhook Repository 实现。
 *
 * <p>委托 {@link JobWebhookMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobWebhookRepositoryImpl implements JobWebhookRepository {

  private final JobWebhookMapper jobWebhookMapper;

  @Override
  public List<JobWebhook> selectActiveByEventType(String eventType) {
    return jobWebhookMapper.selectActiveByEventType(eventType);
  }

  @Override
  public List<JobWebhook> selectActiveByEventAndJob(String eventType, String jobId) {
    return jobWebhookMapper.selectActiveByEventAndJob(eventType, jobId);
  }
}
