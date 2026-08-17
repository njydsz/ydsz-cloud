package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobArtifact;
import com.njydsz.cronjob.infra.mapper.job.JobArtifactMapper;
import com.njydsz.cronjob.infra.repository.JobArtifactRepository;

/**
 * 任务产物 Repository 实现。
 *
 * <p>委托 {@link JobArtifactMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobArtifactRepositoryImpl implements JobArtifactRepository {

  private final JobArtifactMapper jobArtifactMapper;

  @Override
  public List<JobArtifact> selectByLogId(String logId) {
    return jobArtifactMapper.selectByLogId(logId);
  }

  @Override
  public int cleanExpired(LocalDateTime before, int limit) {
    return jobArtifactMapper.cleanExpired(before, limit);
  }
}
