package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.core.alert.AlertType;
import com.njydsz.pmis.cronjob.dto.AlertRuleSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobAlertLogDO;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import com.njydsz.pmis.cronjob.mapper.JobAlertLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobAlertRuleMapper;
import com.njydsz.pmis.cronjob.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警规则服务实现（P5 告警 + 监控）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final JobAlertRuleMapper jobAlertRuleMapper;
    private final JobAlertLogMapper jobAlertLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRule(AlertRuleSaveDTO dto) {
        validateRuleConstraints(dto);
        JobAlertRuleDO rule = new JobAlertRuleDO();
        applyDtoToEntity(dto, rule);
        jobAlertRuleMapper.insert(rule);
        log.info("[Alert] 创建告警规则: ruleId={} ruleName={} alertType={}",
                rule.getId(), rule.getRuleName(), rule.getAlertType());
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(String id, AlertRuleSaveDTO dto) {
        JobAlertRuleDO exists = jobAlertRuleMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_alert_not_found");
        }
        validateRuleConstraints(dto);
        applyDtoToEntity(dto, exists);
        jobAlertRuleMapper.updateById(exists);
        log.info("[Alert] 更新告警规则: ruleId={} ruleName={}", id, exists.getRuleName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(String id) {
        JobAlertRuleDO exists = jobAlertRuleMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_alert_not_found");
        }
        jobAlertRuleMapper.deleteById(id);
        log.info("[Alert] 删除告警规则: ruleId={} ruleName={}", id, exists.getRuleName());
    }

    @Override
    public JobAlertRuleDO getRuleById(String id) {
        JobAlertRuleDO rule = jobAlertRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_alert_not_found");
        }
        return rule;
    }

    @Override
    public List<JobAlertRuleDO> listRules() {
        return jobAlertRuleMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleRule(String id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_alert_invalid_enabled");
        }
        JobAlertRuleDO exists = jobAlertRuleMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_alert_not_found");
        }
        exists.setEnabled(enabled);
        jobAlertRuleMapper.updateById(exists);
        log.info("[Alert] 切换规则启用状态: ruleId={} enabled={}", id, enabled);
    }

    @Override
    public List<JobAlertLogDO> queryAlertLogs(String jobId, LocalDateTime since) {
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_alert_invalid_type");
        }
        if (alertType.requiresThreshold() && dto.getThreshold() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_alert_threshold_required",
                    dto.getAlertType());
        }
        if (alertType.requiresTimeWindow() && dto.getTimeWindowMinutes() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_alert_window_required",
                    dto.getAlertType());
        }
    }

    /**
     * 将 DTO 字段应用到实体（创建/更新共用）。
     */
    private void applyDtoToEntity(AlertRuleSaveDTO dto, JobAlertRuleDO rule) {
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
