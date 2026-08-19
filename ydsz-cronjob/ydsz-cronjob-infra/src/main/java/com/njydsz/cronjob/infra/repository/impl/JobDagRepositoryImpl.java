package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.infra.entity.dag.JobDag;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;

/**
 * DAG 工作流定义 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobDagRepository} 接口，封装 JobDagMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobDagRepositoryImpl implements JobDagRepository {

  private final JobDagMapper jobDagMapper;

  private final CronjobConverter converter;

  @Override
  public Optional<JobDagVO> findByDagKey(String dagKey) {
    return Optional.ofNullable(jobDagMapper.selectByDagKey(dagKey)).map(converter::entityToVO);
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

  // ===== Web 层查询方法实现 =====

  @Override
  public Optional<JobDagVO> findById(String dagId) {
    return Optional.ofNullable(jobDagMapper.selectById(dagId)).map(converter::entityToVO);
  }

  // ===== 实体 CRUD 实现（Service 层 DAG 管理使用） =====

  @Override
  public JobDag selectById(String id) {
    return jobDagMapper.selectById(id);
  }

  @Override
  public int insert(JobDag dag) {
    return jobDagMapper.insert(dag);
  }

  @Override
  public int updateById(JobDag dag) {
    return jobDagMapper.updateById(dag);
  }

  @Override
  public int deleteById(String id) {
    return jobDagMapper.deleteById(id);
  }

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
}
