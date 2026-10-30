package com.njydsz.cronjob.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.log.JobLogContent;
import com.njydsz.cronjob.domain.repository.JobLogContentRepository;
import com.njydsz.cronjob.domain.vo.JobLogContentVO;
import com.njydsz.cronjob.infra.mapper.log.JobLogContentMapper;

/**
 * 任务日志内容 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JobLogContentRepositoryImpl implements JobLogContentRepository {

  /** 批量刷新阈值（每 100 条 flush 一次，控制内存占用） */
  private static final int BATCH_FLUSH_SIZE = 100;

  private final JobLogContentMapper jobLogContentMapper;
  private final CronjobConverter converter;
  private final SqlSessionFactory sqlSessionFactory;

  @Override
  public List<JobLogContentVO> findByLogId(String logId, int offset, int limit) {
    return converter.jobLogContentListToVO(
        jobLogContentMapper.selectByLogId(logId, offset, limit));
  }

  @Override
  public List<JobLogContentVO> findAfterLine(String logId, int fromLineNo) {
    return converter.jobLogContentListToVO(
        jobLogContentMapper.selectAfterLine(logId, fromLineNo));
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

  @Override
  public String insert(JobLogContentVO vo) {
    JobLogContent entity = converter.voToEntity(vo);
    jobLogContentMapper.insert(entity);
    return entity.getId();
  }

  /**
   * 批量新增：使用 MyBatis BATCH 执行器将 N 次 round-trip 降为 ceil(N/100)+1 次。
   */
  @Override
  public void batchInsert(List<JobLogContentVO> contents) {
    if (contents == null || contents.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    try (SqlSession batchSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
      JobLogContentMapper batchMapper = batchSession.getMapper(JobLogContentMapper.class);
      int count = 0;
      for (JobLogContentVO vo : contents) {
        JobLogContent entity = converter.voToEntity(vo);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        batchMapper.insert(entity);
        count++;
        if (count % BATCH_FLUSH_SIZE == 0) {
          batchSession.flushStatements();
        }
      }
      batchSession.commit();
      log.info("[JobLog] 批量写入日志行 count={}", contents.size());
    }
  }
}
