package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobArtifactRepository;
import com.njydsz.cronjob.domain.vo.JobArtifactVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.job.JobArtifact;
import com.njydsz.cronjob.infra.mapper.job.JobArtifactMapper;

/**
 * 任务产物 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class JobArtifactRepositoryImpl implements JobArtifactRepository {

  private final JobArtifactMapper jobArtifactMapper;
  private final CronjobConverter converter;

  @Override
  public List<JobArtifactVO> findByLogId(String logId) {
    return converter.jobArtifactListToVO(jobArtifactMapper.selectByLogId(logId));
  }

  @Override
  public Optional<JobArtifactVO> findById(String id) {
    return Optional.ofNullable(jobArtifactMapper.selectById(id))
        .map(converter::entityToVO);
  }

  @Override
  public int cleanExpired(LocalDateTime before, int limit) {
    return jobArtifactMapper.cleanExpired(before, limit);
  }

  @Override
  public String insert(JobArtifactVO vo) {
    JobArtifact entity = converter.voToEntity(vo);
    jobArtifactMapper.insert(entity);
    return entity.getId();
  }
}
