package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobWebhookRepository;
import com.njydsz.cronjob.domain.vo.JobWebhookVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobWebhookMapper;
import com.njydsz.cronjob.infra.repository.JobRepository;

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

  // ===== Web 层 CRUD 方法实现 =====

  @Override
  public String create(JobWebhookVO vo) {
    jobWebhookMapper.insert(converter.voToEntity(vo));
    return vo.getId();
  }

  @Override
  public void update(JobWebhookVO vo) {
    jobWebhookMapper.updateById(converter.voToEntity(vo));
  }

  @Override
  public void deleteById(String id, java.time.LocalDateTime updatedAt) {
    com.njydsz.cronjob.domain.entity.job.JobWebhook update = new com.njydsz.cronjob.domain.entity.job.JobWebhook();
    update.setId(id);
    update.setDeleted(1);
    update.setUpdatedAt(updatedAt);
    jobWebhookMapper.updateById(update);
  }

  @Override
  public java.util.Optional<JobWebhookVO> findById(String id) {
    java.util.Optional<com.njydsz.cronjob.domain.entity.job.JobWebhook> entityOpt =
        java.util.Optional.ofNullable(jobWebhookMapper.selectById(id));
    return entityOpt.map(converter::entityToVO);
  }

  @Override
  public JobRepository.PageResult<JobWebhookVO> pageBy(int pageNum, int size, String eventType, String jobKey) {
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.njydsz.cronjob.domain.entity.job.JobWebhook> pageObj =
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, size);
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.njydsz.cronjob.domain.entity.job.JobWebhook> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(com.njydsz.cronjob.domain.entity.job.JobWebhook::getDeleted, 0);
    if (eventType != null && !eventType.isBlank()) {
      wrapper.eq(com.njydsz.cronjob.domain.entity.job.JobWebhook::getEventType, eventType);
    }
    if (jobKey != null && !jobKey.isBlank()) {
      wrapper.eq(com.njydsz.cronjob.domain.entity.job.JobWebhook::getJobKey, jobKey);
    }
    wrapper.orderByDesc(com.njydsz.cronjob.domain.entity.job.JobWebhook::getCreatedAt);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.njydsz.cronjob.domain.entity.job.JobWebhook> result =
        jobWebhookMapper.selectPage(pageObj, wrapper);
    return new JobRepository.PageResult<>(converter.jobWebhookListToVO(result.getRecords()), result.getTotal());
  }
}
