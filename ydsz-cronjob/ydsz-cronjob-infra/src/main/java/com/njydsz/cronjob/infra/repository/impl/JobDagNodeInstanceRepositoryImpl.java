package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobDagNodeInstanceRepository;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;

/**
 * DAG 节点实例 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagNodeInstanceRepositoryImpl implements JobDagNodeInstanceRepository {

  private final JobDagNodeInstanceMapper jobDagNodeInstanceMapper;
  private final CronjobConverter converter;

  @Override
  public List<JobDagNodeInstanceVO> findByDagInstanceId(String dagInstanceId) {
    return converter.jobDagNodeInstanceListToVO(
        jobDagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId));
  }

  @Override
  public Optional<JobDagNodeInstanceVO> findByDagInstanceAndJob(String dagInstanceId, String jobId) {
    return Optional.ofNullable(
            jobDagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, jobId))
        .map(converter::entityToVO);
  }

  @Override
  public List<JobDagNodeInstanceVO> findAllByDagInstanceAndJob(String dagInstanceId, String jobId) {
    return converter.jobDagNodeInstanceListToVO(
        jobDagNodeInstanceMapper.selectAllByDagInstanceAndJob(dagInstanceId, jobId));
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
  public void insertBatch(List<JobDagNodeInstanceVO> vos) {
    List<JobDagNodeInstance> entities = vos.stream()
        .map(converter::voToEntity)
        .toList();
    jobDagNodeInstanceMapper.insertBatch(entities);
  }
}
