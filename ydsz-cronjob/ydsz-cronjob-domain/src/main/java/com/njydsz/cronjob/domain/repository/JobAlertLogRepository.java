package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobAlertLogVO;

/**
 * 告警日志 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface JobAlertLogRepository {

  /**
   * 根据规则 ID 和时间查询告警日志。
   *
   * @param ruleId 告警规则 ID
   * @param since 查询时间起点（含此时间之后的记录）
   * @return 告警日志 VO 列表，无记录时返回空列表
   */
  List<JobAlertLogVO> findByRuleIdSince(String ruleId, LocalDateTime since);

  /**
   * 根据任务 ID 和时间查询告警日志。
   *
   * @param jobId 任务 ID
   * @param since 查询时间起点（含此时间之后的记录）
   * @return 告警日志 VO 列表，无记录时返回空列表
   */
  List<JobAlertLogVO> findByJobIdSince(String jobId, LocalDateTime since);

  /**
   * 清理过期告警日志。
   *
   * @param before 过期时间分界点（删除此时间之前的记录）
   * @param limit 单批最多删除条数
   * @return 实际删除的告警日志条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  /**
   * 写入告警派发记录。
   *
   * @param vo 告警日志 VO
   * @return 受影响行数
   */
  int insert(JobAlertLogVO vo);
}
