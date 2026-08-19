package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobDagVersionRepository;
import com.njydsz.cronjob.domain.vo.JobDagVersionVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.dag.JobDagVersionMapper;

/**
 * DAG 版本历史 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobDagVersionRepository} 接口，封装 JobDagVersionMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagVersionRepositoryImpl implements JobDagVersionRepository {

  private final JobDagVersionMapper jobDagVersionMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobDagVersionVO> findByVersionDesc(String dagId) {
    // P0-FIX: Mapper.selectByVersionDesc 需要 (dagId, limit) 两个参数，原实现漏传 limit
    return converter.jobDagVersionListToVO(jobDagVersionMapper.selectByVersionDesc(dagId, 100));
  }

  @Override
  public Optional<Integer> findMaxVersion(String dagId) {
    return Optional.ofNullable(jobDagVersionMapper.selectMaxVersion(dagId));
  }

  @Override
  public Optional<JobDagVersionVO> findByVersion(String dagId, Integer version) {
    return Optional.ofNullable(jobDagVersionMapper.selectByVersion(dagId, version))
        .map(converter::entityToVO);
  }
}
