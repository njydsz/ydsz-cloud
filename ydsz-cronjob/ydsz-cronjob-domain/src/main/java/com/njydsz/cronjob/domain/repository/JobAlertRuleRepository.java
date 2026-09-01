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
   * @return 启用的告警规则 VO 列表；无记录时返回空列表
   */
  List<JobAlertRuleVO> findAllEnabled();

  /**
   * 根据 jobId 查询专属规则 + 全局规则。
   *
   * @param jobId 任务 ID（匹配该任务专属规则 + 全局规则）
   * @return 匹配的告警规则 VO 列表
   */
  List<JobAlertRuleVO> findByJobIdOrGlobal(String jobId);

  /**
   * 根据告警类型查询规则。
   *
   * @param alertType 告警类型（如 FAIL_RATE / DURATION_P95 / SLA_WARNING 等）
   * @return 该告警类型的规则 VO 列表
   */
  List<JobAlertRuleVO> findByAlertType(String alertType);

  /**
   * 根据 jobId 查询 SLA 相关规则。
   *
   * @param jobId 任务 ID
   * @return 该任务关联的 SLA 告警规则 VO 列表
   */
  List<JobAlertRuleVO> findSlaRulesByJobId(String jobId);

  /**
   * 按 ID 查询规则。
   *
   * @param id 告警规则 ID
   * @return 告警规则 VO；不存在时返回 {@code Optional.empty()}
   */
  Optional<JobAlertRuleVO> findById(String id);

  /**
   * CAS 更新 last_alert_at（冷却窗口去重）。
   *
   * @param ruleId 告警规则 ID
   * @param now 当前时间（作为新的 lastAlertAt）
   * @param cooldownBefore 冷却时间窗口起点（仅当 lastAlertAt &lt; cooldownBefore 时更新）
   * @return 受影响行数（0 表示仍在冷却窗口内，未更新）
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
   * @param id 要删除的告警规则 ID
   * @return 受影响行数（0 表示规则不存在或已被删除）
   */
  int deleteById(String id);
}
