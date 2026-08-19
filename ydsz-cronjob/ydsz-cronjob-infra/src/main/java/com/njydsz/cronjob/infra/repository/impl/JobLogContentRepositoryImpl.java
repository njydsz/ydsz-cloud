package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.infra.entity.log.JobLogContent;
import com.njydsz.cronjob.domain.repository.JobLogContentRepository;
import com.njydsz.cronjob.domain.vo.JobLogContentVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.log.JobLogContentMapper;

/**
 * 任务日志内容 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link JobLogContentRepository} 接口，封装 JobLogContentMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class JobLogContentRepositoryImpl implements JobLogContentRepository {

  private final JobLogContentMapper jobLogContentMapper;

  private final CronjobConverter converter;

  @Override
  public List<JobLogContentVO> findByLogId(String logId, int offset, int limit) {
    return converter.jobLogContentListToVO(jobLogContentMapper.selectByLogId(logId, offset, limit));
  }

  @Override
  public List<JobLogContentVO> findAfterLine(String logId, int fromLineNo) {
    return converter.jobLogContentListToVO(jobLogContentMapper.selectAfterLine(logId, fromLineNo));
  }

  @Override
  public int countByLogId(String logId) {
    return jobLogContentMapper.countByLogId(logId);
  }

  @Override
  public List<JobLogContentVO> findByLogIdAndKeyword(String logId, String keyword, int offset, int limit) {
    return converter.jobLogContentListToVO(
        jobLogContentMapper.selectByLogIdAndKeyword(logId, keyword, offset, limit));
  }

  @Override
  public int cleanExpiredLogs(LocalDateTime before, int limit) {
    return jobLogContentMapper.cleanExpiredLogs(before, limit);
  }

  // ===== 实体方法实现 =====

  @Override
  public int insert(JobLogContent content) {
    return jobLogContentMapper.insert(content);
  }

  @Override
  public List<JobLogContent> selectByLogId(String logId, int offset, int limit) {
    return jobLogContentMapper.selectByLogId(logId, offset, limit);
  }

  @Override
  public List<JobLogContent> selectAfterLine(String logId, int fromLineNo) {
    return jobLogContentMapper.selectAfterLine(logId, fromLineNo);
  }

  @Override
  public List<JobLogContent> selectByLogIdAndKeyword(
      String logId, String keyword, int offset, int limit) {
    return jobLogContentMapper.selectByLogIdAndKeyword(logId, keyword, offset, limit);
  }
}
