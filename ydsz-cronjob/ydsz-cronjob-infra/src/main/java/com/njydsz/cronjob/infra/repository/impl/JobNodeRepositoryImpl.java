package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

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

  private final JobNodeMapper jobNodeMapper;

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
}
