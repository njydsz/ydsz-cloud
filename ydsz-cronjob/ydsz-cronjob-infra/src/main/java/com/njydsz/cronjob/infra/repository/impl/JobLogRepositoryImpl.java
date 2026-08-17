package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.infra.repository.JobLogRepository;

/**
 * 任务执行日志 Repository 实现。
 *
 * <p>委托 {@link JobLogMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobLogRepositoryImpl implements JobLogRepository {

  private final JobLogMapper jobLogMapper;

  @Override
  public List<JobLog> selectTimedOutLogs(LocalDateTime now, int limit) {
    return jobLogMapper.selectTimedOutLogs(now, limit);
  }

  @Override
  public List<JobLog> selectApproachingSlaLogs(LocalDateTime now, int limit) {
    return jobLogMapper.selectApproachingSlaLogs(now, limit);
  }

  @Override
  public int markTimeout(String id, LocalDateTime endTime, long durationMs, String errorMessage) {
    return jobLogMapper.markTimeout(id, endTime, durationMs, errorMessage);
  }

  @Override
  public List<JobLog> selectSlowLogs(LocalDateTime since, int limit) {
    return jobLogMapper.selectSlowLogs(since, limit);
  }

  @Override
  public int markSlow(String logId, long slowThresholdMs) {
    return jobLogMapper.markSlow(logId, slowThresholdMs);
  }

  @Override
  public List<JobLog> selectRunningByNode(String nodeId) {
    return jobLogMapper.selectRunningByNode(nodeId);
  }

  @Override
  public int markFailedByNodeOffline(String nodeId, LocalDateTime now) {
    return jobLogMapper.markFailedByNodeOffline(nodeId, now);
  }

  @Override
  public List<String> selectRunningNodeIds() {
    return jobLogMapper.selectRunningNodeIds();
  }

  @Override
  public Map<String, Object> countByJobIdSince(String jobId, LocalDateTime since) {
    return jobLogMapper.countByJobIdSince(jobId, since);
  }

  @Override
  public Long selectDurationP95(String jobId, LocalDateTime since) {
    return jobLogMapper.selectDurationP95(jobId, since);
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobLogMapper.cleanExpiredLogs(before, limit);
  }
}
