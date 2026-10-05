package com.njydsz.cronjob.server.service.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.enums.JobTaskStatusEnum;
import com.njydsz.cronjob.domain.repository.JobTaskRepository;
import com.njydsz.cronjob.domain.service.JobTaskQueryService;
import com.njydsz.cronjob.domain.vo.JobTaskVO;

/**
 * MapReduce 子任务查询 Service 实现（server 层）。
 *
 * <p>通过 JobTaskRepository 查询子任务信息，遵循 DDD 分层。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobTaskQueryServiceImpl implements JobTaskQueryService {

  private static final int COLLECTION_CAPACITY = 16;

  private final JobTaskRepository jobTaskRepository;

  @Override
  public List<JobTaskVO> findByLogId(String logId) {
    return jobTaskRepository.findByLogId(logId);
  }

  @Override
  public PageResponse<List<JobTaskVO>> pageByLogId(String logId, int page, int size) {
    return jobTaskRepository.pageByLogId(logId, page, size);
  }

  @Override
  public Map<String, Object> getProgress(String logId) {
    int total = jobTaskRepository.countByLogId(logId);
    int pending = jobTaskRepository.countByLogIdAndStatus(logId, JobTaskStatusEnum.PENDING.name());
    int running = jobTaskRepository.countByLogIdAndStatus(logId, JobTaskStatusEnum.RUNNING.name());
    int success = jobTaskRepository.countByLogIdAndStatus(logId, JobTaskStatusEnum.SUCCESS.name());
    int failed = jobTaskRepository.countByLogIdAndStatus(logId, JobTaskStatusEnum.FAILED.name());
    Map<String, Object> result = new HashMap<>(COLLECTION_CAPACITY);
    result.put("total", total);
    result.put("pending", pending);
    result.put("running", running);
    result.put("success", success);
    result.put("failed", failed);
    result.put("progressPercent", total > 0 ? (int) ((success + failed) * 100.0 / total) : 0);
    return result;
  }
}
