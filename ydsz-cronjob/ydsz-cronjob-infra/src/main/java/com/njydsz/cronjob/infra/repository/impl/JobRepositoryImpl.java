package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;

/**
 * 任务定义 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobRepository} 接口，封装 JobMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobRepositoryImpl implements JobRepository {

  private final JobMapper jobMapper;

  private final CronjobConverter converter;

  @Override
  public Optional<JobVO> findByJobKey(String jobKey) {
    return Optional.ofNullable(jobMapper.selectByJobKey(jobKey)).map(converter::entityToVO);
  }

  @Override
  public List<JobVO> findAllNormal() {
    return converter.jobListToVO(jobMapper.selectAllNormal());
  }

  @Override
  public List<JobVO> findDueJobs(LocalDateTime now, int limit) {
    return converter.jobListToVO(jobMapper.selectDueJobs(now, limit));
  }

  @Override
  public List<JobVO> findDueJobsInWindow(LocalDateTime now, LocalDateTime windowEnd, int limit) {
    return converter.jobListToVO(jobMapper.selectDueJobsInWindow(now, windowEnd, limit));
  }

  @Override
  public int advanceNextFireTime(
      String id,
      LocalDateTime oldNextFireTime,
      LocalDateTime newNextFireTime,
      LocalDateTime lastFireTime) {
    return jobMapper.advanceNextFireTime(id, oldNextFireTime, newNextFireTime, lastFireTime);
  }

  @Override
  public int updateStats(
      String id,
      LocalDateTime lastFireTime,
      LocalDateTime nextFireTime,
      Long fireCount,
      Long successCount,
      Long failCount,
      String status) {
    return jobMapper.updateStats(id, lastFireTime, nextFireTime, fireCount, successCount, failCount, status);
  }

  @Override
  public int resetConsecutiveFail(String id) {
    return jobMapper.resetConsecutiveFail(id);
  }

  @Override
  public int incrementConsecutiveFail(String id) {
    return jobMapper.incrementConsecutiveFail(id);
  }

  @Override
  public int markAutoPaused(String id) {
    return jobMapper.markAutoPaused(id);
  }

  @Override
  public Optional<Integer> findConsecutiveFailCount(String id) {
    return Optional.ofNullable(jobMapper.selectConsecutiveFailCount(id));
  }

  @Override
  public List<JobVO> findAutoResumeCandidates(LocalDateTime now) {
    return converter.jobListToVO(jobMapper.selectAutoResumeCandidates(now));
  }

  @Override
  public int resumeAutoPaused(String id) {
    return jobMapper.resumeAutoPaused(id);
  }
}
