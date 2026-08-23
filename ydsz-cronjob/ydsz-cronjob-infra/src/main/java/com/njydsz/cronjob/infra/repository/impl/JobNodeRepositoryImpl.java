package com.njydsz.cronjob.infra.repository.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobNodeRepository;
import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.job.JobNode;
import com.njydsz.cronjob.infra.mapper.job.JobNodeMapper;

/**
 * 调度节点 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobNodeRepository} 接口，封装 JobNodeMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobNodeRepositoryImpl implements JobNodeRepository {

  /** 默认心跳阈值（秒）：60 秒 */
  private static final long DEFAULT_HEARTBEAT_THRESHOLD_SECONDS = 60L;


  private final JobNodeMapper jobNodeMapper;
  private final CronjobConverter converter;

  @Override
  public int markStaleOnlineAsOffline(LocalDateTime staleThreshold) {
    return jobNodeMapper.markStaleOnlineAsOffline(staleThreshold);
  }

  @Override
  public List<String> findStaleOnlineNodeIds(LocalDateTime staleThreshold) {
    return jobNodeMapper.selectStaleOnlineNodeIds(staleThreshold);
  }

  @Override
  public int deleteStaleOfflineNodes(LocalDateTime offlineThreshold) {
    return jobNodeMapper.deleteStaleOfflineNodes(offlineThreshold);
  }

  @Override
  public List<JobNodeVO> findOnlineNodes() {
    // 默认心跳阈值：60 秒（与 DbNodeDiscoveryStrategy 离线阈值默认值保持一致）
    long thresholdSeconds = DEFAULT_HEARTBEAT_THRESHOLD_SECONDS;
    LocalDateTime cutoff = LocalDateTime.now().minusSeconds(thresholdSeconds);
    LambdaQueryWrapper<JobNode> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(JobNode::getStatus, "ONLINE")
        .ge(JobNode::getLastHeartbeat, cutoff)
        .orderByAsc(JobNode::getNodeId);
    return converter.jobNodeListToVO(jobNodeMapper.selectList(wrapper));
  }

  @Override
  public List<JobNodeVO> findByStatus(String status) {
    LambdaQueryWrapper<JobNode> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
      wrapper.eq(JobNode::getStatus, status);
    }
    return converter.jobNodeListToVO(jobNodeMapper.selectList(wrapper));
  }

  @Override
  public Optional<JobNodeVO> findById(String nodeId) {
    LambdaQueryWrapper<JobNode> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(JobNode::getNodeId, nodeId);
    JobNode entity = jobNodeMapper.selectOne(wrapper);
    return Optional.ofNullable(converter.entityToVO(entity));
  }

  @Override
  public void insert(JobNodeVO node) {
    JobNode entity = converter.voToEntity(node);
    jobNodeMapper.insert(entity);
  }

  @Override
  public int updateByNodeId(JobNodeVO node) {
    JobNode entity = converter.voToEntity(node);
    return jobNodeMapper.updateByNodeId(entity);
  }

  @Override
  public int updateHeartbeat(
      String nodeId,
      LocalDateTime lastHeartbeat,
      int runningCount,
      BigDecimal cpuUsage,
      BigDecimal memUsagePct,
      String status) {
    return jobNodeMapper.updateHeartbeat(
        nodeId, lastHeartbeat, runningCount, cpuUsage, memUsagePct, status);
  }

  @Override
  public int updateStatus(String nodeId, String status, LocalDateTime lastHeartbeat) {
    return jobNodeMapper.updateStatus(nodeId, status, lastHeartbeat);
  }
}
