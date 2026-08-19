package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.cronjob.infra.entity.log.JobLog;
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

  // ===== Web 层查询方法实现 =====

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
  public long countByTimeRange(LocalDateTime start, LocalDateTime end) {
    LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(JobLog::getStartTime, start)
        .le(JobLog::getStartTime, end);
    return jobLogMapper.selectCount(wrapper);
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

  // ===== 实体方法实现 =====

  @Override
  public Page<JobLog> selectPage(Page<JobLog> page, LambdaQueryWrapper<JobLog> wrapper) {
    return jobLogMapper.selectPage(page, wrapper);
  }

  @Override
  public int insert(JobLog log) {
    return jobLogMapper.insert(log);
  }

  @Override
  public int updateById(JobLog log) {
    return jobLogMapper.updateById(log);
  }
}
