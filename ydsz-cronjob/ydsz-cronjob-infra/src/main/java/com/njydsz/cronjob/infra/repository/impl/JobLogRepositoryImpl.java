package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;

/**
 * 任务执行日志 Repository 实现（Infra 层）。
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
  public List<JobLogVO> findStuckTasks(LocalDateTime threshold, int limit) {
    return converter.jobLogListToVO(jobLogMapper.selectStuckTasks(threshold, limit));
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
  public Map<String, Object> countSince(LocalDateTime since) {
    return jobLogMapper.countSince(since);
  }

  @Override
  public Optional<Long> findDurationP95Global(LocalDateTime since) {
    return Optional.ofNullable(jobLogMapper.selectDurationP95Global(since));
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobLogMapper.cleanExpiredLogs(before, limit);
  }

  @Override
  public long countByStatusAfter(String status, LocalDateTime startAfter) {
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    if (status != null) {
      wrapper.eq(JobLog::getStatus, status);
    }
    if (startAfter != null) {
      wrapper.ge(JobLog::getStartTime, startAfter);
    }
    return jobLogMapper.selectCount(wrapper);
  }

  @Override
  public List<JobLogVO> findRecentFailures(int limit) {
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(JobLog::getStatus, JobLog.STATUS_FAILED)
        .orderByDesc(JobLog::getStartTime)
        .last("LIMIT " + Math.min(limit, 100));
    return converter.jobLogListToVO(jobLogMapper.selectList(wrapper));
  }

  @Override
  public List<JobLogVO> findByJobKey(String jobKey, int limit) {
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(JobLog::getJobKey, jobKey)
        .orderByDesc(JobLog::getCreatedAt)
        .last("LIMIT " + Math.min(limit, 100));
    return converter.jobLogListToVO(jobLogMapper.selectList(wrapper));
  }

  @Override
  public Optional<JobLogVO> findLatestByJobKey(String jobKey) {
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(JobLog::getJobKey, jobKey)
        .orderByDesc(JobLog::getCreatedAt)
        .last("LIMIT 1");
    JobLogVO vo = converter.entityToVO(jobLogMapper.selectOne(wrapper));
    return Optional.ofNullable(vo);
  }

  @Override
  public long countByTimeRange(LocalDateTime start, LocalDateTime end) {
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(JobLog::getStartTime, start)
        .le(JobLog::getStartTime, end);
    return jobLogMapper.selectCount(wrapper);
  }

  @Override
  public JobRepository.PageResult<JobLogVO> pageByJobKeyAndStatus(String jobKey, String status, int page, int size) {
    Page<JobLog> pageObj = new Page<>(page, size);
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    if (jobKey != null && !jobKey.isBlank()) {
      wrapper.eq(JobLog::getJobKey, jobKey);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(JobLog::getStatus, status);
    }
    wrapper.orderByDesc(JobLog::getStartTime);
    Page<JobLog> result = jobLogMapper.selectPage(pageObj, wrapper);
    return new JobRepository.PageResult<>(converter.jobLogListToVO(result.getRecords()), result.getTotal());
  }

  @Override
  public String insert(JobLogVO vo) {
    JobLog entity = converter.voToEntity(vo);
    jobLogMapper.insert(entity);
    return entity.getId();
  }

  @Override
  public int update(JobLogVO vo) {
    JobLog entity = converter.voToEntity(vo);
    return jobLogMapper.updateById(entity);
  }

  @Override
  public Optional<JobLogVO> findById(String id) {
    return Optional.ofNullable(jobLogMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public int updateById(JobLogVO vo) {
    JobLog entity = converter.voToEntity(vo);
    return jobLogMapper.updateById(entity);
  }

  @Override
  public long countByJobIdAndStatus(String jobId, String status) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobLog> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    if (jobId != null && !jobId.isBlank()) {
      wrapper.eq(JobLog::getJobId, jobId);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(JobLog::getStatus, status);
    }
    return jobLogMapper.selectCount(wrapper);
  }

  @Override
  public List<JobLogVO> findByJobIdSince(String jobId, LocalDateTime since) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobLog> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(JobLog::getJobId, jobId)
        .ge(JobLog::getCreatedAt, since)
        .orderByDesc(JobLog::getCreatedAt);
    return converter.jobLogListToVO(jobLogMapper.selectList(wrapper));
  }

  @Override
  public Optional<JobLogVO> findLatestByJobKeyAndRunning(String jobKey) {
    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobLog> wrapper =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
    wrapper.eq(JobLog::getJobKey, jobKey)
        .eq(JobLog::getStatus, "RUNNING")
        .eq(JobLog::getDeleted, 0)
        .orderByDesc(JobLog::getCreatedAt)
        .last("LIMIT 1");
    return Optional.ofNullable(jobLogMapper.selectOne(wrapper))
        .map(converter::jobLogToVO);
  }
}
