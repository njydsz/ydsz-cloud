package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;

/**
 * 告警规则 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobAlertRuleRepository {

  /**
   * 查询所有启用的告警规则。
   *
   * @return 返回值说明
   */
  List<JobAlertRuleVO> findAllEnabled();

  /**
   * 根据 jobId 查询专属规则 + 全局规则。
   *
   * @param jobId 参数说明
   * @return 返回值说明
   */
  List<JobAlertRuleVO> findByJobIdOrGlobal(String jobId);

  /**
   * 根据告警类型查询规则。
   *
   * @param alertType 参数说明
   * @return 返回值说明
   */
  List<JobAlertRuleVO> findByAlertType(String alertType);

  /**
   * 根据 jobId 查询 SLA 相关规则。
   *
   * @param jobId 参数说明
   * @return 返回值说明
   */
  List<JobAlertRuleVO> findSlaRulesByJobId(String jobId);

  /**
   * 按 ID 查询规则。
   *
   * @param id 参数说明
   * @return 返回值说明
   */
  Optional<JobAlertRuleVO> findById(String id);

  /**
   * CAS 更新 last_alert_at（冷却窗口去重）。
   *
   * @param ruleId 参数说明
   * @param now 参数说明
   * @param cooldownBefore 参数说明
   * @return 返回值说明
   */
  int updateLastAlertAtIfNotInCooldown(String ruleId, LocalDateTime now, LocalDateTime cooldownBefore);

  /**
   * 新增规则。
   *
   * @param dto 告警规则保存 DTO
   * @return 新规则 ID
   */
  String insert(AlertRuleSaveDTO dto);

  /**
   * 按 ID 更新规则。
   *
   * @param dto 告警规则保存 DTO（必须含 id）
   * @return 受影响行数
   */
  int update(AlertRuleSaveDTO dto);

  /**
   * 按 ID 删除规则（逻辑删除）。
   *
   * @param id 参数说明
   * @return 返回值说明
   */
  int deleteById(String id);
}
