package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobAlertLogVO;

/**
 * 告警日志 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobAlertLogRepository {

  /**
   * 根据规则 ID 和时间查询告警日志。
   *
   * @param ruleId 参数说明
   * @param since 参数说明
   * @return 返回值说明
   */
  List<JobAlertLogVO> findByRuleIdSince(String ruleId, LocalDateTime since);

  /**
   * 根据任务 ID 和时间查询告警日志。
   *
   * @param jobId 参数说明
   * @param since 参数说明
   * @return 返回值说明
   */
  List<JobAlertLogVO> findByJobIdSince(String jobId, LocalDateTime since);

  /**
   * 清理过期告警日志。
   *
   * @param before 参数说明
   * @param limit 参数说明
   * @return 返回值说明
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
