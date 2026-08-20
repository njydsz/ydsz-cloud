package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.DagInstanceRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.dag.JobDagInstance;
import com.njydsz.cronjob.infra.mapper.dag.JobDagInstanceMapper;

/**
 * DAG 实例 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link DagInstanceRepository} 接口，封装 JobDagInstanceMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class DagInstanceRepositoryImpl implements DagInstanceRepository {

  private final JobDagInstanceMapper dagInstanceMapper;
  private final CronjobConverter converter;

  @Override
  public Optional<JobDagInstanceVO> findById(String id) {
    return Optional.ofNullable(dagInstanceMapper.selectById(id))
        .map(converter::entityToVO);
  }

  @Override
  public int markPaused(String instanceId) {
    return dagInstanceMapper.markPaused(instanceId);
  }

  @Override
  public int markResumed(String instanceId) {
    return dagInstanceMapper.markResumed(instanceId);
  }

  @Override
  public int markCanceled(String instanceId, LocalDateTime finishedAt, long durationMs) {
    return dagInstanceMapper.markCanceled(instanceId, finishedAt, durationMs);
  }

  @Override
  public int update(JobDagInstanceVO vo) {
    JobDagInstance entity = converter.voToEntity(vo);
    return dagInstanceMapper.updateById(entity);
  }

  @Override
  public String insert(JobDagInstanceVO vo) {
    JobDagInstance entity = converter.voToEntity(vo);
    dagInstanceMapper.insert(entity);
    return entity.getId();
  }

  @Override
  public List<JobDagInstanceVO> findPendingInstances(LocalDateTime now, int limit) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobDagInstance> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(JobDagInstance::getStatus, "PENDING")
        .le(JobDagInstance::getNextFireTime, now)
        .orderByAsc(JobDagInstance::getNextFireTime)
        .last("LIMIT " + limit);
    return converter.jobDagInstanceListToVO(dagInstanceMapper.selectList(wrapper));
  }

  @Override
  public int markRunning(String instanceId, LocalDateTime startedAt) {
    return dagInstanceMapper.markRunning(instanceId, startedAt);
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
    return dagInstanceMapper.markFinished(
        instanceId, status, finishedAt, durationMs, errorMessage, totalNodes, successNodes, failedNodes, skippedNodes);
  }

  @Override
  public int mergeContextAtomic(String instanceId, String mergeJson) {
    return dagInstanceMapper.mergeContextAtomic(instanceId, mergeJson);
  }

  @Override
  public int updateResultStats(String dagId, boolean success) {
    return dagInstanceMapper.updateResultStats(dagId, success);
  }
}
