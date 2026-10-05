package com.njydsz.cronjob.server.service.query;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.service.JobDiagnosisService;
import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * 任务诊断查询 Service 实现（server 层）。
 *
 * <p>通过 JobLogRepository 查询任务执行日志，遵循 DDD 分层。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDiagnosisServiceImpl implements JobDiagnosisService {

  private final JobLogRepository jobLogRepository;

  @Override
  public Optional<JobLogVO> findLatestByJobKey(String jobKey) {
    return jobLogRepository.findLatestByJobKey(jobKey);
  }

  @Override
  public List<JobLogVO> findByJobKey(String jobKey, int limit) {
    return jobLogRepository.findByJobKey(jobKey, limit);
  }
}
