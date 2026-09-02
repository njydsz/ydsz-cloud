package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobHistoryVO;

/**
 * 任务历史记录 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface JobHistoryRepository {

  /**
   * 根据任务 ID 查询历史版本列表（按版本号降序）。
   *
   * @param jobId 任务 ID
   * @return 历史版本 VO 列表（按版本号降序），无记录时返回空列表
   */
  List<JobHistoryVO> findByJobIdOrderByVersionDesc(String jobId);

  /**
   * 根据任务 ID 和版本号查询历史记录。
   *
   * @param jobId 任务 ID
   * @param version 版本号
   * @return 匹配的历史记录；不存在时返回 {@code Optional.empty()}
   */
  Optional<JobHistoryVO> findByVersion(String jobId, Integer version);

  /**
   * 清理过期历史记录。
   *
   * @param before 过期时间分界点（删除此时间之前的记录）
   * @param limit 单批最多删除条数
   * @return 实际删除的历史记录条数
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
