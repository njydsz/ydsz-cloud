package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.entity.job.JobAlertLog;

/**
 * 告警日志 Repository。
 *
 * <p>封装 {@code ydsz_job_alert_log} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobAlertLogRepository {

  /**
   * 根据规则 ID 和时间查询告警日志。
   *
   * @param ruleId 规则 ID
   * @param since 时间起点
   * @return 告警日志列表
   */
  List<JobAlertLog> selectByRuleIdSince(String ruleId, LocalDateTime since);

  /**
   * 根据任务 ID 和时间查询告警日志。
   *
   * @param jobId 任务 ID
   * @param since 时间起点
   * @return 告警日志列表
   */
  List<JobAlertLog> selectByJobIdSince(String jobId, LocalDateTime since);

  /**
   * 清理过期告警日志。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);
}
