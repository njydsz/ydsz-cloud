package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobHistoryVO;

/**
 * 任务历史记录 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobHistoryRepository {

  /**
   * 根据任务 ID 查询历史版本列表（按版本号降序）。
   */
  List<JobHistoryVO> findByJobIdOrderByVersionDesc(String jobId);

  /**
   * 根据任务 ID 和版本号查询历史记录。
   */
  Optional<JobHistoryVO> findByVersion(String jobId, Integer version);

  /**
   * 清理过期历史记录。
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  /**
   * 新增历史记录。
   *
   * @param vo 历史记录 VO
   * @return 新记录 ID
   */
  String insert(JobHistoryVO vo);
}
