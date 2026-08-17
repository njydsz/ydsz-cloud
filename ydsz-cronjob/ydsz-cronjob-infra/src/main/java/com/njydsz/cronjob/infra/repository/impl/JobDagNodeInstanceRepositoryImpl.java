package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;
import com.njydsz.cronjob.infra.repository.JobDagNodeInstanceRepository;

/**
 * DAG 节点实例 Repository 实现。
 *
 * <p>委托 {@link JobDagNodeInstanceMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagNodeInstanceRepositoryImpl implements JobDagNodeInstanceRepository {

  private final JobDagNodeInstanceMapper jobDagNodeInstanceMapper;

  @Override
  public List<JobDagNodeInstance> selectByDagInstanceId(String dagInstanceId) {
    return jobDagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
  }

  @Override
  public JobDagNodeInstance selectByDagInstanceAndJob(String dagInstanceId, String jobId) {
    return jobDagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, jobId);
  }

  @Override
  public List<JobDagNodeInstance> selectAllByDagInstanceAndJob(String dagInstanceId, String jobId) {
    return jobDagNodeInstanceMapper.selectAllByDagInstanceAndJob(dagInstanceId, jobId);
  }

  @Override
  public int markRunning(String nodeInstanceId, LocalDateTime startedAt) {
    return jobDagNodeInstanceMapper.markRunning(nodeInstanceId, startedAt);
  }

  @Override
  public int markFinished(
      String nodeInstanceId,
      String status,
      LocalDateTime finishedAt,
      long durationMs,
      String resultJson,
      String errorMessage,
      String logId) {
    return jobDagNodeInstanceMapper.markFinished(
        nodeInstanceId, status, finishedAt, durationMs, resultJson, errorMessage, logId);
  }

  @Override
  public int markSkipped(String nodeInstanceId) {
    return jobDagNodeInstanceMapper.markSkipped(nodeInstanceId);
  }

  @Override
  public int markRetry(String nodeInstanceId) {
    return jobDagNodeInstanceMapper.markRetry(nodeInstanceId);
  }

  @Override
  public void insertBatch(List<JobDagNodeInstance> instances) {
    jobDagNodeInstanceMapper.insertBatch(instances);
  }
}
