package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.infra.entity.job.JobAlertRule;

/**
 * 告警规则 Repository。
 *
 * <p>封装 {@code ydsz_job_alert_rule} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobAlertRuleRepository {

  /**
   * 查询所有启用的告警规则。
   *
   * @return 启用的告警规则列表
   */
  List<JobAlertRule> selectAllEnabled();

  /**
   * 根据 jobId 查询专属规则 + 全局规则。
   *
   * @param jobId 任务 ID
   * @return 匹配的告警规则列表
   */
  List<JobAlertRule> selectByJobIdOrGlobal(String jobId);

  /**
   * 根据告警类型查询规则。
   *
   * @param alertType 告警类型
   * @return 匹配的告警规则列表
   */
  List<JobAlertRule> selectByAlertType(String alertType);

  /**
   * 根据 jobId 查询 SLA 相关规则。
   *
   * @param jobId 任务 ID
   * @return SLA 规则列表
   */
  List<JobAlertRule> selectSlaRulesByJobId(String jobId);

  /**
   * CAS 更新 last_alert_at（冷却窗口去重）。
   *
   * @param ruleId 规则 ID
   * @param now 当前时间
   * @param cooldownBefore 冷却窗口起点
   * @return 受影响行数（1=可以告警；0=在冷却期内）
   */
  int updateLastAlertAtIfNotInCooldown(String ruleId, LocalDateTime now, LocalDateTime cooldownBefore);
}
