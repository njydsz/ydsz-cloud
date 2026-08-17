package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.entity.job.JobArtifact;

/**
 * 任务产物 Repository。
 *
 * <p>封装 {@code ydsz_job_artifact} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobArtifactRepository {

  /**
   * 根据日志 ID 查询产物列表。
   *
   * @param logId 日志 ID
   * @return 产物列表
   */
  List<JobArtifact> selectByLogId(String logId);

  /**
   * 清理过期产物记录。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpired(LocalDateTime before, int limit);
}
