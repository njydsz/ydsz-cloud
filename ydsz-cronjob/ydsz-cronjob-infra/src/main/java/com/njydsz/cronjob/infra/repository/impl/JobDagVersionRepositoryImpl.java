package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;
import com.njydsz.cronjob.infra.mapper.dag.JobDagVersionMapper;
import com.njydsz.cronjob.infra.repository.JobDagVersionRepository;

/**
 * DAG 版本历史 Repository 实现。
 *
 * <p>委托 {@link JobDagVersionMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagVersionRepositoryImpl implements JobDagVersionRepository {

  private final JobDagVersionMapper jobDagVersionMapper;

  @Override
  public List<JobDagVersion> selectByVersionDesc(String dagId) {
    return jobDagVersionMapper.selectByVersionDesc(dagId);
  }

  @Override
  public Integer selectMaxVersion(String dagId) {
    return jobDagVersionMapper.selectMaxVersion(dagId);
  }

  @Override
  public JobDagVersion selectByVersion(String dagId, Integer version) {
    return jobDagVersionMapper.selectByVersion(dagId, version);
  }
}
