package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.DagNodeInstanceRepository;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;

/**
 * DAG 节点实例 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link DagNodeInstanceRepository} 接口，封装 JobDagNodeInstanceMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class DagNodeInstanceRepositoryImpl implements DagNodeInstanceRepository {

  private final JobDagNodeInstanceMapper dagNodeInstanceMapper;
  private final CronjobConverter converter;

  @Override
  public JobDagNodeInstanceVO findById(String id) {
    JobDagNodeInstance entity = dagNodeInstanceMapper.selectById(id);
    return entity == null ? null : converter.entityToVO(entity);
  }

  @Override
  public List<JobDagNodeInstanceVO> findByDagInstanceId(String dagInstanceId) {
    List<JobDagNodeInstance> entities = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return converter.jobDagNodeInstanceListToVO(entities);
  }

  @Override
  public int markSkipped(String nodeId) {
    return dagNodeInstanceMapper.markSkipped(nodeId);
  }

  @Override
  public int markFinished(
      String nodeId,
      String status,
      LocalDateTime finishedAt,
      long durationMs,
      String resultJson,
      String errorMessage,
      String logId) {
    return dagNodeInstanceMapper.markFinished(
        nodeId, status, finishedAt, durationMs, resultJson, errorMessage, logId);
  }

  @Override
  public int markRunning(String nodeId, LocalDateTime startedAt) {
    return dagNodeInstanceMapper.markRunning(nodeId, startedAt);
  }

  @Override
  public int insert(JobDagNodeInstanceVO vo) {
    JobDagNodeInstance entity = converter.voToEntity(vo);
    return dagNodeInstanceMapper.insert(entity);
  }

  @Override
  public List<JobDagNodeInstanceVO> findByDagInstanceIdAndStatus(String dagInstanceId, String status) {
    List<JobDagNodeInstance> entities =
        dagNodeInstanceMapper.selectByDagInstanceIdAndStatus(dagInstanceId, status);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return converter.jobDagNodeInstanceListToVO(entities);
  }

  @Override
  public JobDagNodeInstanceVO findByDagInstanceAndJob(String dagInstanceId, String jobId) {
    JobDagNodeInstance entity = dagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, jobId);
    return entity == null ? null : converter.entityToVO(entity);
  }

  @Override
  public JobDagNodeInstanceVO findByDagInstanceAndJobKey(String dagInstanceId, String jobKey) {
    JobDagNodeInstance entity = dagNodeInstanceMapper.selectByDagInstanceAndJobKey(dagInstanceId, jobKey);
    return entity == null ? null : converter.entityToVO(entity);
  }

  @Override
  public int markRetry(String nodeId) {
    return dagNodeInstanceMapper.markRetry(nodeId);
  }

  @Override
  public int updateById(JobDagNodeInstanceVO vo) {
    JobDagNodeInstance entity = converter.voToEntity(vo);
    return dagNodeInstanceMapper.updateById(entity);
  }

  @Override
  public List<JobDagNodeInstanceVO> selectActiveByJobId(String jobId) {
    List<JobDagNodeInstance> entities = dagNodeInstanceMapper.selectActiveByJobId(jobId);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return converter.jobDagNodeInstanceListToVO(entities);
  }

  @Override
  public int markWaitingForApproval(String nodeId, LocalDateTime waitingAt) {
    return dagNodeInstanceMapper.markWaitingForApproval(nodeId, waitingAt);
  }

  @Override
  public List<JobDagNodeInstanceVO> findWaitingApprovalNodes(int limit) {
    List<JobDagNodeInstance> entities = dagNodeInstanceMapper.findWaitingApprovalNodes(limit);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return converter.jobDagNodeInstanceListToVO(entities);
  }
}
