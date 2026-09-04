package com.njydsz.cronjob.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobDagVersionRepository;
import com.njydsz.cronjob.domain.vo.JobDagVersionVO;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;
import com.njydsz.cronjob.infra.mapper.dag.JobDagVersionMapper;

/**
 * DAG 版本历史 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class JobDagVersionRepositoryImpl implements JobDagVersionRepository {

  private final JobDagVersionMapper jobDagVersionMapper;
  private final CronjobConverter converter;

  @Override
  public List<JobDagVersionVO> findByVersionDesc(String dagId, int limit) {
    return converter.jobDagVersionListToVO(jobDagVersionMapper.selectByVersionDesc(dagId, limit));
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

  @Override
  public String insert(JobDagVersionVO vo) {
    JobDagVersion entity = converter.voToEntity(vo);
    jobDagVersionMapper.insert(entity);
    return entity.getId();
  }
}
