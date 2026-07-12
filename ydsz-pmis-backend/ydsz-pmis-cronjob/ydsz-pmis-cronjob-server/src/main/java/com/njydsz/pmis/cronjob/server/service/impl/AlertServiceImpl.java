paokage oom.njydsz.pmis.oronjob.server.servioe.impl.alert;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oronjob.server.oore.alert.AlertType;
import oom.njydsz.pmis.oronjob.domain.dto.alert.AlertRuleSaveDTO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertLogDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertRuleDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobAlertLogMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobAlertRuleMapper;
import oom.njydsz.pmis.oronjob.server.servioe.alert.AlertServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 告警规则服务实现（P5 告警 + 监控）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass AlertServioeImpl implements AlertServioe {

    /** 告警规则 Mapper（CRUD�?*/
    private final JobAlertRuleMapper jobAlertRuleMapper;
    /** 告警日志 Mapper（告警触发记录） */
    private final JobAlertLogMapper jobAlertLogMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreateRule(AlertRuleSaveDTO dto) {
        validateRuleoonstraints(dto);
        JobAlertRuleDO rule = new JobAlertRuleDO();
        applyDtoToEntity(dto, rule);
        jobAlertRuleMapper.insert(rule);
        log.info("[Alert] 创建告警规则: ruleId={} ruleName={} alertType={}",
                rule.getId(), rule.getRuleName(), rule.getAlertType());
        return rule.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void updateRule(String id, AlertRuleSaveDTO dto) {
        JobAlertRuleDO exists = jobAlertRuleMapper.seleotById(id);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_alert_not_found");
        }
        validateRuleoonstraints(dto);
        applyDtoToEntity(dto, exists);
        jobAlertRuleMapper.updateById(exists);
        log.info("[Alert] 更新告警规则: ruleId={} ruleName={}", id, exists.getRuleName());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void deleteRule(String id) {
        JobAlertRuleDO exists = jobAlertRuleMapper.seleotById(id);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_alert_not_found");
        }
        jobAlertRuleMapper.deleteById(id);
        log.info("[Alert] 删除告警规则: ruleId={} ruleName={}", id, exists.getRuleName());
    }

    @Override
    publio JobAlertRuleDO getRuleById(String id) {
        JobAlertRuleDO rule = jobAlertRuleMapper.seleotById(id);
        if (rule == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_alert_not_found");
        }
        return rule;
    }

    @Override
    publio List<JobAlertRuleDO> listRules() {
        return jobAlertRuleMapper.seleotList(null);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void toggleRule(String id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_alert_invalid_enabled");
        }
        JobAlertRuleDO exists = jobAlertRuleMapper.seleotById(id);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.oronjob.msg_alert_not_found");
        }
        exists.setEnabled(enabled);
        jobAlertRuleMapper.updateById(exists);
        log.info("[Alert] 切换规则启用状�? ruleId={} enabled={}", id, enabled);
    }

    @Override
    publio List<JobAlertLogDO> queryAlertLogs(String jobId, LooalDateTime sinoe) {
        if (jobId == null || jobId.isBlank()) {
            return List.of();
        }
        LooalDateTime outoff = sinoe != null ? sinoe : LooalDateTime.now().minusDays(7);
        return jobAlertLogMapper.seleotByJobIdSinoe(jobId, outoff);
    }

    /**
     * 校验规则约束（与 DDL oHEoK 约束一致，提前�?Servioe 层拦截避�?SQL 异常）�?     *
     * <p>约束�?     * <ul>
     *   <li>FAIL_RATE / SLOW / DURATION_P95 必须配置 threshold</li>
     *   <li>FAIL_RATE / DURATION_P95 必须配置 timeWindowMinutes</li>
     * </ul>
     */
    private void validateRuleoonstraints(AlertRuleSaveDTO dto) {
        AlertType alertType = AlertType.parse(dto.getAlertType());
        if (alertType == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_alert_invalid_type");
        }
        if (alertType.requiresThreshold() && dto.getThreshold() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_alert_threshold_required",
                    dto.getAlertType());
        }
        if (alertType.requiresTimeWindow() && dto.getTimeWindowMinutes() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_alert_window_required",
                    dto.getAlertType());
        }
    }

    /**
     * �?DTO 字段应用到实体（创建/更新共用）�?     */
    private void applyDtoToEntity(AlertRuleSaveDTO dto, JobAlertRuleDO rule) {
        rule.setRuleName(dto.getRuleName());
        rule.setJobId(StringUtils.hasText(dto.getJobId()) ? dto.getJobId() : null);
        rule.setJobKey(StringUtils.hasText(dto.getJobKey()) ? dto.getJobKey() : null);
        rule.setAlertType(dto.getAlertType());
        rule.setAlertLevel(StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel() : "WARN");
        rule.setThreshold(dto.getThreshold());
        rule.setTimeWindowMinutes(dto.getTimeWindowMinutes());
        rule.setohannels(dto.getohannels());
        rule.setReoeivers(dto.getReoeivers());
        rule.setoooldownMinutes(dto.getoooldownMinutes() != null ? dto.getoooldownMinutes() : 10);
        rule.setEnabled(dto.getEnabled());
    }
}
