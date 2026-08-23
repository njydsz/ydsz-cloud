package com.njydsz.cronjob.server.service.impl.alert;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.repository.JobAlertLogRepository;
import com.njydsz.cronjob.domain.repository.JobAlertRuleRepository;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;
import com.njydsz.cronjob.server.core.alert.AlertTrigger;
import com.njydsz.cronjob.server.core.alert.AlertType;
import com.njydsz.cronjob.server.service.alert.AlertService;

/**
 * 任务告警服务实现。
 *
 * <p>基于告警规则 ({@code ydsz_job_alert_rule}) 与告警日志 ({@code ydsz_job_alert_log}) 提供任务告警的订阅、
 *
 * <p>触发、抑制、发送全流程。
 *
 * <p>支持邮件/短信/企业微信/钉钉多渠道告警分发，告警风暴抑制（同一规则 5 分钟内仅告警 1 次）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {
  /** 默认回看天数 */
  private static final int DEFAULT_LOOKBACK_DAYS = 7;


  /** 告警规则 Repository（CRUD） */
  private final JobAlertRuleRepository jobAlertRuleRepository;

  /** 告警日志 Repository（告警触发记录） */
  private final JobAlertLogRepository jobAlertLogRepository;

  /** 告警触发器（用于规则变更时失效本地缓存） */
  private final AlertTrigger alertTrigger;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String createRule(AlertRuleSaveDTO dto) {
    validateRuleConstraints(dto);
    String ruleId = jobAlertRuleRepository.insert(dto);
    // P1-P5: 规则变更后失效本地缓存，确保新规则下次告警触发时加载
    alertTrigger.invalidateAlertRuleCache(dto.getJobId());
    log.info(
        "[Alert] 创建告警规则: ruleId={} ruleName={} alertType={}",
        ruleId,
        dto.getRuleName(),
        dto.getAlertType());
    return ruleId;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void updateRule(String id, AlertRuleSaveDTO dto) {
    JobAlertRuleVO exists =
        jobAlertRuleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    SysException.builder()
                        .resultCode(YdszResultCode.NOT_FOUND)
                        .message("error.cronjob.msg_alert_not_found")
                        .build());
    validateRuleConstraints(dto);
    dto.setId(id);
    jobAlertRuleRepository.update(dto);
    // P1-P5: 规则变更后失效本地缓存
    alertTrigger.invalidateAlertRuleCache(exists.getJobId());
    log.info("[Alert] 更新告警规则: ruleId={} ruleName={}", id, dto.getRuleName());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteRule(String id) {
    JobAlertRuleVO exists =
        jobAlertRuleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    SysException.builder()
                        .resultCode(YdszResultCode.NOT_FOUND)
                        .message("error.cronjob.msg_alert_not_found")
                        .build());
    String jobId = exists.getJobId();
    jobAlertRuleRepository.deleteById(id);
    // P1-P5: 规则变更后失效本地缓存
    alertTrigger.invalidateAlertRuleCache(jobId);
    log.info("[Alert] 删除告警规则: ruleId={} ruleName={}", id, exists.getRuleName());
  }

  @Override
  public JobAlertRuleVO getRuleById(String id) {
    return jobAlertRuleRepository
        .findById(id)
        .orElseThrow(
            () ->
                SysException.builder()
                    .resultCode(YdszResultCode.NOT_FOUND)
                    .message("error.cronjob.msg_alert_not_found")
                    .build());
  }

  @Override
  public List<JobAlertRuleVO> listRules() {
    return jobAlertRuleRepository.findAllEnabled();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void toggleRule(String id, Integer enabled) {
    if (enabled == null || (enabled != 0 && enabled != 1)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_alert_invalid_enabled")
          .build();
    }
    JobAlertRuleVO exists =
        jobAlertRuleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    SysException.builder()
                        .resultCode(YdszResultCode.NOT_FOUND)
                        .message("error.cronjob.msg_alert_not_found")
                        .build());
    AlertRuleSaveDTO dto = new AlertRuleSaveDTO();
    dto.setId(id);
    dto.setRuleName(exists.getRuleName());
    dto.setJobId(exists.getJobId());
    dto.setJobKey(exists.getJobKey());
    dto.setAlertType(exists.getAlertType());
    dto.setAlertLevel(exists.getAlertLevel());
    dto.setThreshold(exists.getThreshold() != null ? exists.getThreshold().doubleValue() : null);
    dto.setTimeWindowMinutes(exists.getTimeWindowMinutes());
    dto.setChannels(exists.getChannels());
    dto.setReceivers(exists.getReceivers());
    dto.setCooldownMinutes(exists.getCooldownMinutes());
    dto.setEnabled(enabled);
    jobAlertRuleRepository.update(dto);
    // P1-P5: 规则变更后失效本地缓存
    alertTrigger.invalidateAlertRuleCache(exists.getJobId());
    log.info("[Alert] 切换规则启用状态: ruleId={} enabled={}", id, enabled);
  }

  @Override
  public List<JobAlertLogVO> queryAlertLogs(String jobId, LocalDateTime since) {
    if (jobId == null || jobId.isBlank()) {
      return List.of();
    }
    LocalDateTime cutoff = since != null ? since : LocalDateTime.now().minusDays(DEFAULT_LOOKBACK_DAYS);
    return jobAlertLogRepository.findByJobIdSince(jobId, cutoff);
  }

  /**
   * 校验规则约束（与 DDL CHECK 约束一致，提前在 Service 层拦截避免 SQL 异常）。
   *
   * <p>约束：
   *
   * <ul>
   *   <li>FAIL_RATE / SLOW / DURATION_P95 必须配置 threshold
   *   <li>FAIL_RATE / DURATION_P95 必须配置 timeWindowMinutes
   * </ul>
   */
  private void validateRuleConstraints(AlertRuleSaveDTO dto) {
    AlertType alertType = AlertType.parse(dto.getAlertType());
    if (alertType == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_alert_invalid_type")
          .build();
    }
    if (alertType.requiresThreshold() && dto.getThreshold() == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_alert_threshold_required")
          .params(dto.getAlertType())
          .build();
    }
    if (alertType.requiresTimeWindow() && dto.getTimeWindowMinutes() == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_alert_window_required")
          .params(dto.getAlertType())
          .build();
    }
  }
}
