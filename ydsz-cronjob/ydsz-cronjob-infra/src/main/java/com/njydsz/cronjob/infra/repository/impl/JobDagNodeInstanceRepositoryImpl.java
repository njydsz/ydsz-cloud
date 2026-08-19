package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.domain.repository.JobDagNodeInstanceRepository;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;

/**
 * DAG 节点实例 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobDagNodeInstanceRepository} 接口，封装 JobDagNodeInstanceMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
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
    return Optional.ofNullable(jobDagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, jobId))
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
    // VO → Entity 转换后批量插入
    // Note: insertBatch from the original used domain entities; VOs are converted via converter
    // Since CronjobConverter doesn't have a voToEntity for JobDagNodeInstance, we use the mapper directly
    // This is a CUD operation that accepts VO and converts internally
    jobDagNodeInstanceMapper.insertBatch(converter.jobDagNodeInstanceVOsToEntities(vos));
  }

  @Override
  public List<JobDagNodeInstance> selectByDagInstanceId(String dagInstanceId) {
    return jobDagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
  }
}
