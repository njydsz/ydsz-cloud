package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobHistory;
import com.njydsz.cronjob.infra.mapper.job.JobHistoryMapper;
import com.njydsz.cronjob.infra.repository.JobHistoryRepository;

/**
 * 任务历史记录 Repository 实现。
 *
 * <p>委托 {@link JobHistoryMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobHistoryRepositoryImpl implements JobHistoryRepository {

  private final JobHistoryMapper jobHistoryMapper;

  @Override
  public List<JobHistory> selectByJobIdOrderByVersionDesc(String jobId) {
    return jobHistoryMapper.selectByJobIdOrderByVersionDesc(jobId);
  }

  @Override
  public JobHistory selectByVersion(String jobId, Integer version) {
    return jobHistoryMapper.selectByVersion(jobId, version);
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobHistoryMapper.cleanExpiredLogs(before, limit);
  }
}
