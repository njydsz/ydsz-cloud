package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.JobHistory;
import com.njydsz.cronjob.domain.repository.JobHistoryRepository;
import com.njydsz.cronjob.domain.vo.JobHistoryVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.JobHistoryMapper;

/**
 * 任务历史记录 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobHistoryRepository} 接口，封装 JobHistoryMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobHistoryRepositoryImpl implements JobHistoryRepository {

  private final JobHistoryMapper jobHistoryMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobHistoryVO> findByJobIdOrderByVersionDesc(String jobId) {
    return converter.jobHistoryListToVO(jobHistoryMapper.selectByJobIdOrderByVersionDesc(jobId));
  }

  @Override
  public Optional<JobHistoryVO> findByVersion(String jobId, Integer version) {
    return Optional.ofNullable(jobHistoryMapper.selectByVersion(jobId, version))
        .map(converter::entityToVO);
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobHistoryMapper.cleanExpiredLogs(before, limit);
  }
}
