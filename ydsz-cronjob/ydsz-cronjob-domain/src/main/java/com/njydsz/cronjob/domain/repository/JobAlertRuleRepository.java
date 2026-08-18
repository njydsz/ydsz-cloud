package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;

/**
 * 告警规则 Repository（domain 层契约）。
 *
 * <p>定义任务告警规则的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobAlertRuleVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobAlertRuleRepository {

  /**
   * 查询所有启用的告警规则。
   *
   * @return 启用的告警规则 VO 列表
   */
  List<JobAlertRuleVO> findAllEnabled();

  /**
   * 根据 jobId 查询专属规则 + 全局规则。
   *
   * @param jobId 任务 ID
   * @return 匹配的告警规则 VO 列表
   */
  List<JobAlertRuleVO> findByJobIdOrGlobal(String jobId);

  /**
   * 根据告警类型查询规则。
   *
   * @param alertType 告警类型
   * @return 匹配的告警规则 VO 列表
   */
  List<JobAlertRuleVO> findByAlertType(String alertType);

  /**
   * 根据 jobId 查询 SLA 相关规则。
   *
   * @param jobId 任务 ID
   * @return SLA 规则 VO 列表
   */
  List<JobAlertRuleVO> findSlaRulesByJobId(String jobId);

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
