package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.entity.job.JobHistory;

/**
 * 任务历史记录 Repository。
 *
 * <p>封装 {@code ydsz_job_history} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobHistoryRepository {

  /**
   * 根据任务 ID 查询历史版本列表（按版本号降序）。
   *
   * @param jobId 任务 ID
   * @return 历史版本列表
   */
  List<JobHistory> selectByJobIdOrderByVersionDesc(String jobId);

  /**
   * 根据任务 ID 和版本号查询历史记录。
   *
   * @param jobId 任务 ID
   * @param version 版本号
   * @return 历史记录，不存在时返回 null
   */
  JobHistory selectByVersion(String jobId, Integer version);

  /**
   * 清理过期历史记录。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);
}
