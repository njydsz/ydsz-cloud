package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;

/**
 * DAG 工作流定义 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class JobDagRepositoryImpl implements JobDagRepository {

  private final JobDagMapper jobDagMapper;
  private final CronjobConverter converter;

  @Override
  public Optional<JobDagVO> findByDagKey(String dagKey) {
    return Optional.ofNullable(jobDagMapper.selectByDagKey(dagKey))
        .map(converter::entityToVO);
  }

  @Override
  public Optional<JobDagVO> findById(String dagId) {
    return Optional.ofNullable(jobDagMapper.selectById(dagId))
        .map(converter::entityToVO);
  }

  @Override
  public List<JobDagVO> findCronEnabledDags() {
    return converter.jobDagListToVO(jobDagMapper.selectCronEnabledDags());
  }

  @Override
  public List<JobDagVO> findEnabledDags() {
    return converter.jobDagListToVO(jobDagMapper.selectEnabledDags());
  }

  @Override
  public int updateFireStats(String dagId, LocalDateTime lastFireTime, LocalDateTime nextFireTime) {
    return jobDagMapper.updateFireStats(dagId, lastFireTime, nextFireTime);
  }

  @Override
  public int updateResultStats(String dagId, boolean success) {
    return jobDagMapper.updateResultStats(dagId, success);
  }

  @Override
  public int advanceNextFireTime(String dagId, LocalDateTime oldNext, LocalDateTime newNext) {
    return jobDagMapper.advanceNextFireTime(dagId, oldNext, newNext);
  }

  @Override
  public String insert(JobDagSaveDTO dto) {
    JobDag entity = converter.dtoToEntity(dto);
    jobDagMapper.insert(entity);
    return entity.getId();
  }

  @Override
  public int update(JobDagSaveDTO dto) {
    JobDag entity = converter.dtoToEntity(dto);
    return jobDagMapper.updateById(entity);
  }

  @Override
  public int deleteById(String dagId) {
    return jobDagMapper.deleteById(dagId);
  }

  @Override
  public int updateById(JobDagVO vo) {
    JobDag entity = converter.voToEntity(vo);
    return jobDagMapper.updateById(entity);
  }

  @Override
  public String insert(JobDagVO vo) {
    JobDag entity = converter.voToEntity(vo);
    jobDagMapper.insert(entity);
    return entity.getId();
  }
}
