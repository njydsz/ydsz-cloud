package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.cronjob.infra.repository.JobDagRepository;

/**
 * DAG 工作流定义 Repository 实现。
 *
 * <p>委托 {@link JobDagMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagRepositoryImpl implements JobDagRepository {

  private final JobDagMapper jobDagMapper;

  @Override
  public JobDag selectByDagKey(String dagKey) {
    return jobDagMapper.selectByDagKey(dagKey);
  }

  @Override
  public List<JobDag> selectCronEnabledDags() {
    return jobDagMapper.selectCronEnabledDags();
  }

  @Override
  public List<JobDag> selectEnabledDags() {
    return jobDagMapper.selectEnabledDags();
  }

  @Override
  public int updateFireStats(String dagId, LocalDateTime lastFireTime, LocalDateTime nextFireTime) {
    return jobDagMapper.updateFireStats(dagId, lastFireTime, nextFireTime);
  }

  @Override
  public int updateResultStats(String dagId, boolean success) {
    return jobDagMapper.updateResultStats(dagId, success);
  }
}
