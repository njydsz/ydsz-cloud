package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobArtifact;
import com.njydsz.cronjob.domain.repository.JobArtifactRepository;
import com.njydsz.cronjob.domain.vo.JobArtifactVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobArtifactMapper;

/**
 * 任务产物 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobArtifactRepository} 接口，封装 JobArtifactMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
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
  public int cleanExpired(LocalDateTime before, int limit) {
    return jobArtifactMapper.cleanExpired(before, limit);
  }

  // ===== 实体方法实现 =====

  @Override
  public JobArtifact selectById(String id) {
    return jobArtifactMapper.selectById(id);
  }

  @Override
  public int insert(JobArtifact artifact) {
    return jobArtifactMapper.insert(artifact);
  }

  @Override
  public List<JobArtifact> selectByLogId(String logId) {
    return jobArtifactMapper.selectByLogId(logId);
  }
}
