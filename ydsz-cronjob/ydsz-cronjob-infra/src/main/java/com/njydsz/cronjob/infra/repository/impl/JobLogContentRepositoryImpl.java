package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.infra.mapper.log.JobLogContentMapper;
import com.njydsz.cronjob.infra.repository.JobLogContentRepository;

/**
 * 任务日志内容 Repository 实现。
 *
 * <p>委托 {@link JobLogContentMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobLogContentRepositoryImpl implements JobLogContentRepository {

  private final JobLogContentMapper jobLogContentMapper;

  @Override
  public List<JobLogContent> selectByLogId(String logId, int offset, int limit) {
    return jobLogContentMapper.selectByLogId(logId, offset, limit);
  }

  @Override
  public List<JobLogContent> selectAfterLine(String logId, int fromLineNo) {
    return jobLogContentMapper.selectAfterLine(logId, fromLineNo);
  }

  @Override
  public int countByLogId(String logId) {
    return jobLogContentMapper.countByLogId(logId);
  }

  @Override
  public List<JobLogContent> selectByLogIdAndKeyword(String logId, String keyword, int offset, int limit) {
    return jobLogContentMapper.selectByLogIdAndKeyword(logId, keyword, offset, limit);
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobLogContentMapper.cleanExpiredLogs(before, limit);
  }
}
