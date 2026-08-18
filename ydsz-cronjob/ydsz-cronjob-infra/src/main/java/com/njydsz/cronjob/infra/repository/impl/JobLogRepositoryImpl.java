package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;

/**
 * 任务执行日志 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobLogRepository} 接口，封装 JobLogMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobLogRepositoryImpl implements JobLogRepository {

  private final JobLogMapper jobLogMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobLogVO> findTimedOutLogs(LocalDateTime now, int limit) {
    return converter.jobLogListToVO(jobLogMapper.selectTimedOutLogs(now, limit));
  }

  @Override
  public List<JobLogVO> findApproachingSlaLogs(LocalDateTime now, int limit) {
    return converter.jobLogListToVO(jobLogMapper.selectApproachingSlaLogs(now, limit));
  }

  @Override
  public int markTimeout(String id, LocalDateTime endTime, long durationMs, String errorMessage) {
    return jobLogMapper.markTimeout(id, endTime, durationMs, errorMessage);
  }

  @Override
  public List<JobLogVO> findSlowLogs(LocalDateTime since, int limit) {
    return converter.jobLogListToVO(jobLogMapper.selectSlowLogs(since, limit));
  }

  @Override
  public int markSlow(String logId, long slowThresholdMs) {
    return jobLogMapper.markSlow(logId, slowThresholdMs);
  }

  @Override
  public List<JobLogVO> findRunningByNode(String nodeId) {
    return converter.jobLogListToVO(jobLogMapper.selectRunningByNode(nodeId));
  }

  @Override
  public int markFailedByNodeOffline(String nodeId, LocalDateTime now) {
    return jobLogMapper.markFailedByNodeOffline(nodeId, now);
  }

  @Override
  public List<String> findRunningNodeIds() {
    return jobLogMapper.selectRunningNodeIds();
  }

  @Override
  public Map<String, Object> countByJobIdSince(String jobId, LocalDateTime since) {
    return jobLogMapper.countByJobIdSince(jobId, since);
  }

  @Override
  public Optional<Long> findDurationP95(String jobId, LocalDateTime since) {
    return Optional.ofNullable(jobLogMapper.selectDurationP95(jobId, since));
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobLogMapper.cleanExpiredLogs(before, limit);
  }
}
