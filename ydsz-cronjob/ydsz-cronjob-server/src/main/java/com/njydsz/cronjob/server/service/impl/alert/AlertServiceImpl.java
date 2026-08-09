package com.njydsz.cronjob.server.service.impl.alert;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.entity.job.JobAlertLog;
import com.njydsz.cronjob.domain.entity.job.JobAlertRule;
import com.njydsz.cronjob.infra.mapper.job.JobAlertLogMapper;
import com.njydsz.cronjob.infra.mapper.job.JobAlertRuleMapper;
import com.njydsz.cronjob.server.core.alert.AlertType;
import com.njydsz.cronjob.server.service.alert.AlertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    /** 告警规则 Mapper（CRUD） */
    private final JobAlertRuleMapper jobAlertRuleMapper;
    /** 告警日志 Mapper（告警触发记录） */
    private final JobAlertLogMapper jobAlertLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRule(AlertRuleSaveDTO dto) {
        validateRuleConstraints(dto);
        JobAlertRule rule = new JobAlertRule();
        applyDtoToEntity(dto, rule);
        jobAlertRuleMapper.insert(rule);
        log.info("[Alert] 创建告警规则: ruleId={} ruleName={} alertType={}",
                rule.getId(), rule.getRuleName(), rule.getAlertType());
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(String id, AlertRuleSaveDTO dto) {
        JobAlertRule exists = jobAlertRuleMapper.selectById(id);
        if (exists == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("error.cronjob.msg_alert_not_found")
                .build();
        }
        validateRuleConstraints(dto);
        applyDtoToEntity(dto, exists);
        jobAlertRuleMapper.updateById(exists);
        log.info("[Alert] 更新告警规则: ruleId={} ruleName={}", id, exists.getRuleName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(String id) {
        JobAlertRule exists = jobAlertRuleMapper.selectById(id);
        if (exists == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("error.cronjob.msg_alert_not_found")
                .build();
        }
        jobAlertRuleMapper.deleteById(id);
        log.info("[Alert] 删除告警规则: ruleId={} ruleName={}", id, exists.getRuleName());
    }

    @Override
    public JobAlertRule getRuleById(String id) {
        JobAlertRule rule = jobAlertRuleMapper.selectById(id);
        if (rule == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("error.cronjob.msg_alert_not_found")
                .build();
        }
        return rule;
    }

    @Override
    public List<JobAlertRule> listRules() {
        return jobAlertRuleMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleRule(String id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_alert_invalid_enabled")
                .build();
        }
        JobAlertRule exists = jobAlertRuleMapper.selectById(id);
        if (exists == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .message("error.cronjob.msg_alert_not_found")
                .build();
        }
        exists.setEnabled(enabled);
        jobAlertRuleMapper.updateById(exists);
        log.info("[Alert] 切换规则启用状态: ruleId={} enabled={}", id, enabled);
    }

    @Override
    public List<JobAlertLog> queryAlertLogs(String jobId, LocalDateTime since) {
        if (jobId == null || jobId.isBlank()) {
            return List.of();
        }
        LocalDateTime cutoff = since != null ? since : LocalDateTime.now().minusDays(7);
        return jobAlertLogMapper.selectByJobIdSince(jobId, cutoff);
    }

    /**
     * 校验规则约束（与 DDL CHECK 约束一致，提前在 Service 层拦截避免 SQL 异常）。
     *
     * <p>约束：
     * <ul>
     *   <li>FAIL_RATE / SLOW / DURATION_P95 必须配置 threshold</li>
     *   <li>FAIL_RATE / DURATION_P95 必须配置 timeWindowMinutes</li>
     * </ul>
     */
    private void validateRuleConstraints(AlertRuleSaveDTO dto) {
        AlertType alertType = AlertType.parse(dto.getAlertType());
        if (alertType == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_alert_invalid_type")
                .build();
        }
        if (alertType.requiresThreshold() && dto.getThreshold() == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_alert_threshold_required").params(dto.getAlertType()))
                .build();
        }
        if (alertType.requiresTimeWindow() && dto.getTimeWindowMinutes() == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_alert_window_required").params(dto.getAlertType()))
                .build();
        }
    }

    /**
     * 将 DTO 字段应用到实体（创建/更新共用）。
     */
    private void applyDtoToEntity(AlertRuleSaveDTO dto, JobAlertRule rule) {
        rule.setRuleName(dto.getRuleName());
        rule.setJobId(StringUtils.hasText(dto.getJobId()) ? dto.getJobId() : null);
        rule.setJobKey(StringUtils.hasText(dto.getJobKey()) ? dto.getJobKey() : null);
        rule.setAlertType(dto.getAlertType());
        rule.setAlertLevel(StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel() : "WARN");
        rule.setThreshold(dto.getThreshold());
        rule.setTimeWindowMinutes(dto.getTimeWindowMinutes());
        rule.setChannels(dto.getChannels());
        rule.setReceivers(dto.getReceivers());
        rule.setCooldownMinutes(dto.getCooldownMinutes() != null ? dto.getCooldownMinutes() : 10);
        rule.setEnabled(dto.getEnabled());
    }
}
