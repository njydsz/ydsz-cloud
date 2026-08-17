package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.infra.mapper.job.JobNodeMapper;
import com.njydsz.cronjob.infra.repository.JobNodeRepository;

/**
 * 调度节点 Repository 实现。
 *
 * <p>委托 {@link JobNodeMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobNodeRepositoryImpl implements JobNodeRepository {

  private final JobNodeMapper jobNodeMapper;

  @Override
  public int markStaleOnlineAsOffline(LocalDateTime staleThreshold) {
    return jobNodeMapper.markStaleOnlineAsOffline(staleThreshold);
  }

  @Override
  public List<String> selectStaleOnlineNodeIds(LocalDateTime staleThreshold) {
    return jobNodeMapper.selectStaleOnlineNodeIds(staleThreshold);
  }

  @Override
  public int deleteStaleOfflineNodes(LocalDateTime offlineThreshold) {
    return jobNodeMapper.deleteStaleOfflineNodes(offlineThreshold);
  }
}
