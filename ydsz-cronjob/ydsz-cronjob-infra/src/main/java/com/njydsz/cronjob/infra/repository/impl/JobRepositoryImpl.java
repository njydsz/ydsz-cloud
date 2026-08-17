package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.repository.JobRepository;

/**
 * 任务定义 Repository 实现。
 *
 * <p>委托 {@link JobMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobRepositoryImpl implements JobRepository {

  private final JobMapper jobMapper;

  @Override
  public Job selectByJobKey(String jobKey) {
    return jobMapper.selectByJobKey(jobKey);
  }

  @Override
  public List<Job> selectAllNormal() {
    return jobMapper.selectAllNormal();
  }

  @Override
  public List<Job> selectDueJobs(LocalDateTime now, int limit) {
    return jobMapper.selectDueJobs(now, limit);
  }

  @Override
  public List<Job> selectDueJobsInWindow(LocalDateTime now, LocalDateTime windowEnd, int limit) {
    return jobMapper.selectDueJobsInWindow(now, windowEnd, limit);
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
  public Integer selectConsecutiveFailCount(String id) {
    return jobMapper.selectConsecutiveFailCount(id);
  }

  @Override
  public List<Job> selectAutoResumeCandidates(LocalDateTime now) {
    return jobMapper.selectAutoResumeCandidates(now);
  }

  @Override
  public int resumeAutoPaused(String id) {
    return jobMapper.resumeAutoPaused(id);
  }
}
