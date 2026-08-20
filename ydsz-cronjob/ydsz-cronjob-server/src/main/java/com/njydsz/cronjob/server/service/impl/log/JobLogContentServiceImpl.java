package com.njydsz.cronjob.server.service.impl.log;

import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.repository.JobLogContentRepository;
import com.njydsz.cronjob.domain.vo.JobLogContentVO;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

/**
 * 任务日志内容服务实现。
 *
 * <p>拆分存储任务执行日志的内容（{@code ydsz_job_log_content}）， 与 {@code ydsz_job_log} 1:1 关联，解决大字段导致的 IO 性能问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogContentServiceImpl implements JobLogContentService {

  /** 任务日志内容 Repository（分页/增量查询） */
  private final JobLogContentRepository jobLogContentRepository;

  @Override
  public void batchSave(List<JobLogContentVO> contents) {
    if (contents == null || contents.isEmpty()) {
      return;
    }
    for (JobLogContentVO content : contents) {
      jobLogContentRepository.insert(content);
    }
  }

  @Override
  public List<JobLogContentVO> pageByLogId(String logId, int page, int size) {
    int offset = Math.max(0, (page - 1) * size);
    return jobLogContentRepository.findByLogId(logId, offset, size);
  }

  @Override
  public List<JobLogContentVO> listAfterLine(String logId, int fromLineNo) {
    return jobLogContentRepository.findAfterLine(logId, fromLineNo);
  }

  @Override
  public int countByLogId(String logId) {
    return jobLogContentRepository.countByLogId(logId);
  }

  @Override
  public List<JobLogContentVO> searchByKeyword(String logId, String keyword, int page, int size) {
    if (logId == null || logId.isBlank() || keyword == null || keyword.isBlank()) {
      return Collections.emptyList();
    }
    int offset = Math.max(0, (page - 1) * size);
    return jobLogContentRepository.findByLogIdAndKeyword(logId, keyword, offset, size);
  }
}
