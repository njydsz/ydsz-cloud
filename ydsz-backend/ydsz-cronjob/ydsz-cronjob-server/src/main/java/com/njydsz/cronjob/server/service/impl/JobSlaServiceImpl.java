package com.njydsz.cronjob.server.service.impl.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.dto.alert.JobSlaSaveDTO;
import com.njydsz.cronjob.domain.entity.alert.JobSlaDO;
import com.njydsz.cronjob.domain.entity.job.JobAlertRuleDO;
import com.njydsz.cronjob.infra.mapper.job.JobAlertRuleMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.core.alert.AlertType;
import com.njydsz.cronjob.server.service.alert.JobSlaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SLA 服务实现（P2-7 SLA 管理, P2-2-merge 重构）。
 *
 * <p>P2-2-merge 变更说明：原通过独立的 {@code ydsz_job_sla} 表存储 SLA 规则，
 * 现已合并到 {@code ydsz_job_alert_rule} 表（{@code source_type='SLA'}）。
 * SLA 的三个约束字段映射为 1-3 条 alert_rule 记录：
 * <ul>
 *   <li>{@code max_duration_ms} → alert_type='DURATION_P95', threshold=max_duration_ms</li>
 *   <li>{@code max_fail_rate} → alert_type='FAIL_RATE', threshold=max_fail_rate</li>
 *   <li>{@code min_success_rate} → alert_type='FAIL_RATE', threshold=100-min_success_rate</li>
 * </ul>
 * 由 {@code AlertScanner} 统一扫描并触发告警，{@code SlaScanner} 已移除。
 *
 * <p>对外 API 保持不变：Controller 仍然操作 {@link JobSlaDO}，
 * 内部由本 Service 完成与 alert_rule 的映射转换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSlaServiceImpl implements JobSlaService {

    /** 告警规则 Mapper（P2-2-merge: 替代原 JobSlaMapper） */
    private final JobAlertRuleMapper jobAlertRuleMapper;
    /** 任务日志 Mapper（SLA 违约统计） */
    private final JobLogMapper jobLogMapper;

    /** 默认 SLA 检查时间窗口（分钟） */
    private static final int DEFAULT_WINDOW_MINUTES = 60;

    /** 默认通知通道 */
    private static final String DEFAULT_CHANNELS = "[\"INAPP\"]";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSla(JobSlaSaveDTO dto) {
        validateSlaConstraints(dto);
        // 删除该 job 已有的 SLA 规则（幂等）
        deleteExistingSlaRules(dto.getJobId());
        // 创建新的 SLA 规则
        String alertLevel = mapAlertLevel(StringUtils.hasText(dto.getAlertLevel())
                ? dto.getAlertLevel() : "WARNING");
        int enabled = dto.getEnabled() != null ? dto.getEnabled() : 1;

        if (dto.getMaxDurationMs() != null && dto.getMaxDurationMs() > 0) {
            createSlaAlertRule(dto, AlertType.DURATION_P95, dto.getMaxDurationMs(),
                    alertLevel, enabled, "SLA-最大执行时长");
        }
        if (dto.getMaxFailRate() != null) {
            createSlaAlertRule(dto, AlertType.FAIL_RATE,
                    dto.getMaxFailRate().longValue(), alertLevel, enabled, "SLA-最大失败率");
        }
        if (dto.getMinSuccessRate() != null) {
            // min_success_rate → FAIL_RATE 的互补值
            long complementThreshold = BigDecimal.valueOf(100)
                    .subtract(dto.getMinSuccessRate()).longValue();
            createSlaAlertRule(dto, AlertType.FAIL_RATE,
                    complementThreshold, alertLevel, enabled, "SLA-最小成功率");
        }
        log.info("[Sla] 创建 SLA 规则(代理 alert_rule): jobId={} alertLevel={}",
                dto.getJobId(), alertLevel);
        return dto.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSla(String id, JobSlaSaveDTO dto) {
        // P2-2-merge: id 实际是 jobId（SLA 以 job 为维度）
        validateSlaConstraints(dto);
        deleteExistingSlaRules(id);
        createSla(new JobSlaSaveDTO() {{
            setJobId(id);
            setJobKey(dto.getJobKey());
            setMaxDurationMs(dto.getMaxDurationMs());
            setMaxFailRate(dto.getMaxFailRate());
            setMinSuccessRate(dto.getMinSuccessRate());
            setAlertLevel(dto.getAlertLevel());
            setEnabled(dto.getEnabled());
        }});
        log.info("[Sla] 更新 SLA 规则: jobId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSla(String id) {
        deleteExistingSlaRules(id);
        log.info("[Sla] 删除 SLA 规则: jobId={}", id);
    }

    @Override
    public JobSlaDO getSlaById(String id) {
        List<JobAlertRuleDO> rules = jobAlertRuleMapper.selectSlaRulesByJobId(id);
        if (rules.isEmpty()) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.cronjob.msg_sla_not_found");
        }
        return aggregateSlaFromRules(id, rules);
    }

    @Override
    public List<JobSlaDO> listSla() {
        // 查询所有 source_type='SLA' 的规则，按 jobId 分组聚合
        List<JobAlertRuleDO> allRules = jobAlertRuleMapper.selectList(
                new LambdaQueryWrapper<JobAlertRuleDO>()
                        .eq(JobAlertRuleDO::getSourceType, "SLA")
                        .eq(JobAlertRuleDO::getDeleted, 0));
        Map<String, List<JobAlertRuleDO>> grouped = allRules.stream()
                .collect(Collectors.groupingBy(JobAlertRuleDO::getJobId));
        List<JobSlaDO> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            result.add(aggregateSlaFromRules(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleSla(String id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_sla_invalid_enabled");
        }
        List<JobAlertRuleDO> rules = jobAlertRuleMapper.selectSlaRulesByJobId(id);
        if (rules.isEmpty()) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.cronjob.msg_sla_not_found");
        }
        for (JobAlertRuleDO rule : rules) {
            rule.setEnabled(enabled);
            jobAlertRuleMapper.updateById(rule);
        }
        log.info("[Sla] 切换 SLA 启用状态: jobId={} enabled={}", id, enabled);
    }

    @Override
    public List<SlaViolation> checkViolation(String jobId) {
        List<SlaViolation> violations = new ArrayList<>();
        if (jobId == null || jobId.isBlank()) {
            return violations;
        }
        List<JobAlertRuleDO> rules = jobAlertRuleMapper.selectSlaRulesByJobId(jobId);
        if (rules.isEmpty()) {
            return violations;
        }
        // 过滤启用的规则
        rules = rules.stream()
                .filter(r -> r.getEnabled() != null && r.getEnabled() == 1)
                .toList();
        if (rules.isEmpty()) {
            return violations;
        }

        int windowMinutes = DEFAULT_WINDOW_MINUTES;
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        Map<String, Object> stats = jobLogMapper.countByJobIdSince(jobId, since);
        if (stats == null) {
            return violations;
        }
        long total = toLong(stats.get("total"));
        long failed = toLong(stats.get("failed"));
        if (total <= 0) {
            return violations;
        }
        long success = total - failed;
        double failRate = (failed * 100.0) / total;
        double successRate = (success * 100.0) / total;

        for (JobAlertRuleDO rule : rules) {
            String alertType = rule.getAlertType();
            Long threshold = rule.getThreshold();
            if (threshold == null) {
                continue;
            }
            String ruleName = rule.getRuleName();
            if (AlertType.DURATION_P95.name().equals(alertType)) {
                Long p95Ms = jobLogMapper.selectDurationP95(jobId, since);
                if (p95Ms != null && p95Ms > threshold) {
                    violations.add(new SlaViolation(
                            rule.getId(), jobId, rule.getJobKey(),
                            "MAX_DURATION", String.valueOf(p95Ms),
                            String.valueOf(threshold), rule.getAlertLevel()));
                }
            } else if (AlertType.FAIL_RATE.name().equals(alertType)) {
                // 区分 max_fail_rate 和 min_success_rate（通过 rule_name 判断）
                if (ruleName != null && ruleName.contains("最小成功率")) {
                    if (successRate < threshold) {
                        violations.add(new SlaViolation(
                                rule.getId(), jobId, rule.getJobKey(),
                                "SUCCESS_RATE", String.format("%.2f", successRate),
                                String.valueOf(100 - threshold), rule.getAlertLevel()));
                    }
                } else {
                    if (failRate > threshold) {
                        violations.add(new SlaViolation(
                                rule.getId(), jobId, rule.getJobKey(),
                                "FAIL_RATE", String.format("%.2f", failRate),
                                String.valueOf(threshold), rule.getAlertLevel()));
                    }
                }
            }
        }
        return violations;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 创建一条 SLA 来源的 alert_rule 记录。
     */
    private void createSlaAlertRule(JobSlaSaveDTO dto, AlertType alertType,
                                      long threshold, String alertLevel,
                                      int enabled, String ruleNameSuffix) {
        JobAlertRuleDO rule = new JobAlertRuleDO();
        rule.setRuleName(ruleNameSuffix + "-" + dto.getJobKey());
        rule.setJobId(dto.getJobId());
        rule.setJobKey(dto.getJobKey());
        rule.setAlertType(alertType.name());
        rule.setAlertLevel(alertLevel);
        rule.setThreshold(threshold);
        rule.setTimeWindowMinutes(DEFAULT_WINDOW_MINUTES);
        rule.setChannels(DEFAULT_CHANNELS);
        rule.setReceivers(null);
        rule.setCooldownMinutes(10);
        rule.setEnabled(enabled);
        rule.setSourceType("SLA");
        rule.setTenantId("1");
        jobAlertRuleMapper.insert(rule);
    }

    /**
     * 删除指定任务的所有 SLA 来源 alert_rule（逻辑删除）。
     */
    private void deleteExistingSlaRules(String jobId) {
        List<JobAlertRuleDO> existing = jobAlertRuleMapper.selectSlaRulesByJobId(jobId);
        for (JobAlertRuleDO rule : existing) {
            jobAlertRuleMapper.deleteById(rule.getId());
        }
    }

    /**
     * 将多条 SLA 来源的 alert_rule 聚合为 JobSlaDO。
     */
    private JobSlaDO aggregateSlaFromRules(String jobId, List<JobAlertRuleDO> rules) {
        JobSlaDO sla = new JobSlaDO();
        sla.setId(jobId); // P2-2-merge: id 即 jobId
        sla.setJobId(jobId);
        if (!rules.isEmpty()) {
            sla.setJobKey(rules.get(0).getJobKey());
            sla.setAlertLevel(rules.get(0).getAlertLevel());
            sla.setEnabled(rules.get(0).getEnabled());
        }
        for (JobAlertRuleDO rule : rules) {
            if (AlertType.DURATION_P95.name().equals(rule.getAlertType())) {
                sla.setMaxDurationMs(rule.getThreshold());
            } else if (AlertType.FAIL_RATE.name().equals(rule.getAlertType())) {
                if (rule.getRuleName() != null && rule.getRuleName().contains("最小成功率")) {
                    // min_success_rate 的互补值 → 还原
                    long complement = rule.getThreshold();
                    sla.setMinSuccessRate(BigDecimal.valueOf(100 - complement));
                } else {
                    sla.setMaxFailRate(BigDecimal.valueOf(rule.getThreshold()));
                }
            }
        }
        return sla;
    }

    /**
     * 将 SLA alert_level 映射到 alert_rule alert_level。
     * SLA: INFO/WARNING/CRITICAL → alert_rule: INFO/WARN/CRITICAL
     */
    private String mapAlertLevel(String slaLevel) {
        return switch (slaLevel) {
            case "WARNING" -> "WARN";
            case "INFO", "CRITICAL" -> slaLevel;
            default -> "WARN";
        };
    }

    private void validateSlaConstraints(JobSlaSaveDTO dto) {
        if (dto.getMaxDurationMs() == null && dto.getMaxFailRate() == null
                && dto.getMinSuccessRate() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_sla_no_constraint");
        }
        if (dto.getMaxDurationMs() != null && dto.getMaxDurationMs() <= 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_sla_invalid_duration");
        }
        validateRateRange(dto.getMaxFailRate());
        validateRateRange(dto.getMinSuccessRate());
    }

    private void validateRateRange(BigDecimal rate) {
        if (rate == null) {
            return;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_sla_invalid_rate");
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
