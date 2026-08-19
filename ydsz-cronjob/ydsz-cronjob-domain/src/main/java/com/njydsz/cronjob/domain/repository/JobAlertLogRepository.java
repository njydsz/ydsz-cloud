package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.entity.job.JobAlertLog;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;

/**
 * 告警日志 Repository（domain 层契约）。
 *
 * <p>定义告警派发记录的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobAlertLogVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
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
   * @return 告警日志 VO 列表
   */
  List<JobAlertLogVO> findByRuleIdSince(String ruleId, LocalDateTime since);

  /**
   * 根据任务 ID 和时间查询告警日志。
   *
   * @param jobId 任务 ID
   * @param since 时间起点
   * @return 告警日志 VO 列表
   */
  List<JobAlertLogVO> findByJobIdSince(String jobId, LocalDateTime since);

  /**
   * 清理过期告警日志。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  /**
   * 写入告警派发记录（告警触发链路调用，记录到 ydsz_alert_dispatch）。
   *
   * @param alertLog 告警日志实体（由 {@code AlertDispatcher} 构造）
   * @return 受影响行数
   */
  int insert(JobAlertLog alertLog);
}
