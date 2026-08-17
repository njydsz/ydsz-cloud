package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.dag.JobDagInstance;
import com.njydsz.cronjob.infra.mapper.dag.JobDagInstanceMapper;
import com.njydsz.cronjob.infra.repository.JobDagInstanceRepository;

/**
 * DAG 实例 Repository 实现。
 *
 * <p>委托 {@link JobDagInstanceMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagInstanceRepositoryImpl implements JobDagInstanceRepository {

  private final JobDagInstanceMapper jobDagInstanceMapper;

  @Override
  public List<JobDagInstance> selectByDagId(String dagId, int limit) {
    return jobDagInstanceMapper.selectByDagId(dagId, limit);
  }

  @Override
  public List<JobDagInstance> selectByStatus(String status) {
    return jobDagInstanceMapper.selectByStatus(status);
  }

  @Override
  public int casUpdateStatus(String instanceId, String fromStatus, String toStatus, LocalDateTime updatedAt) {
    return jobDagInstanceMapper.casUpdateStatus(instanceId, fromStatus, toStatus, updatedAt);
  }

  @Override
  public int markRunning(String instanceId, LocalDateTime startedAt) {
    return jobDagInstanceMapper.markRunning(instanceId, startedAt);
  }

  @Override
  public int markFinished(
      String instanceId,
      String status,
      LocalDateTime finishedAt,
      long durationMs,
      String errorMessage,
      int totalNodes,
      int successNodes,
      int failedNodes,
      int skippedNodes) {
    return jobDagInstanceMapper.markFinished(
        instanceId, status, finishedAt, durationMs, errorMessage, totalNodes, successNodes, failedNodes, skippedNodes);
  }

  @Override
  public int updateContext(String instanceId, String contextJson) {
    return jobDagInstanceMapper.updateContext(instanceId, contextJson);
  }

  @Override
  public int mergeContextAtomic(String instanceId, String mergeJson) {
    return jobDagInstanceMapper.mergeContextAtomic(instanceId, mergeJson);
  }

  @Override
  public int countActiveInstances(String status) {
    return jobDagInstanceMapper.countActiveInstances(status);
  }

  @Override
  public int markPaused(String instanceId) {
    return jobDagInstanceMapper.markPaused(instanceId);
  }

  @Override
  public int markResumed(String instanceId) {
    return jobDagInstanceMapper.markResumed(instanceId);
  }

  @Override
  public int markCanceled(String instanceId, LocalDateTime canceledAt, long durationMs) {
    return jobDagInstanceMapper.markCanceled(instanceId, canceledAt, durationMs);
  }
}
