package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobDagInstanceRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.dag.JobDagInstance;
import com.njydsz.cronjob.infra.mapper.dag.JobDagInstanceMapper;

/**
 * DAG 实例 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagInstanceRepositoryImpl implements JobDagInstanceRepository {

  private final JobDagInstanceMapper jobDagInstanceMapper;
  private final CronjobConverter converter;

  @Override
  public List<JobDagInstanceVO> findByDagId(String dagId, int limit) {
    return converter.jobDagInstanceListToVO(jobDagInstanceMapper.selectByDagId(dagId, limit));
  }

  @Override
  public List<JobDagInstanceVO> findByStatus(String status) {
    return converter.jobDagInstanceListToVO(jobDagInstanceMapper.selectByStatus(status));
  }

  @Override
  public Optional<JobDagInstanceVO> findById(String instanceId) {
    return Optional.ofNullable(jobDagInstanceMapper.selectById(instanceId))
        .map(converter::entityToVO);
  }

  @Override
  public int casUpdateStatus(String instanceId, String fromStatus, String toStatus) {
    return jobDagInstanceMapper.casUpdateStatus(instanceId, fromStatus, toStatus);
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
        instanceId, status, finishedAt, durationMs, errorMessage,
        totalNodes, successNodes, failedNodes, skippedNodes);
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
  public int countActiveInstances(String dagId) {
    return jobDagInstanceMapper.countActiveInstances(dagId);
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

  @Override
  public String insert(JobDagInstanceVO vo) {
    JobDagInstance entity = converter.voToEntity(vo);
    jobDagInstanceMapper.insert(entity);
    return entity.getId();
  }
}
