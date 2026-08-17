package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobTask;
import com.njydsz.cronjob.infra.mapper.job.JobTaskMapper;
import com.njydsz.cronjob.infra.repository.JobTaskRepository;

/**
 * MapReduce 子任务 Repository 实现。
 *
 * <p>委托 {@link JobTaskMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobTaskRepositoryImpl implements JobTaskRepository {

  private final JobTaskMapper jobTaskMapper;

  @Override
  public List<JobTask> selectByLogId(String logId) {
    return jobTaskMapper.selectByLogId(logId);
  }

  @Override
  public List<JobTask> selectPendingByLogId(String logId) {
    return jobTaskMapper.selectPendingByLogId(logId);
  }

  @Override
  public int countByLogIdAndStatus(String logId, String status) {
    return jobTaskMapper.countByLogIdAndStatus(logId, status);
  }

  @Override
  public int updateStatus(
      String taskId,
      String status,
      String resultJson,
      String errorMessage,
      LocalDateTime updatedAt) {
    return jobTaskMapper.updateStatus(taskId, status, resultJson, errorMessage, updatedAt);
  }

  @Override
  public int updateExecNodeId(String taskId, String nodeId, LocalDateTime updatedAt) {
    return jobTaskMapper.updateExecNodeId(taskId, nodeId, updatedAt);
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobTaskMapper.cleanExpiredLogs(before, limit);
  }
}
