package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.dto.JobSlaSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobSlaDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobSlaMapper;
import com.njydsz.pmis.cronjob.service.JobSlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SLA 服务实现（P2-7 SLA 管理）。
 *
 * <p>提供 SLA 规则的 CRUD 操作与违约检查能力。违约检查基于 {@code pmis_job_log}
 * 在时间窗口内的聚合统计，由 {@code SlaScanner} 周期性调用或手动触发。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSlaServiceImpl implements JobSlaService {

    private final JobSlaMapper jobSlaMapper;
    private final JobLogMapper jobLogMapper;
    private final CronjobProperties cronjobProperties;

    /** 默认 SLA 检查时间窗口（分钟） */
    private static final int DEFAULT_WINDOW_MINUTES = 60;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSla(JobSlaSaveDTO dto) {
        validateSlaConstraints(dto);
        JobSlaDO sla = new JobSlaDO();
        applyDtoToEntity(dto, sla);
        jobSlaMapper.insert(sla);
        log.info("[Sla] 创建 SLA 规则: slaId={} jobId={} alertLevel={}",
                sla.getId(), sla.getJobId(), sla.getAlertLevel());
        return sla.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSla(String id, JobSlaSaveDTO dto) {
        JobSlaDO exists = jobSlaMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_sla_not_found");
        }
        validateSlaConstraints(dto);
        applyDtoToEntity(dto, exists);
        jobSlaMapper.updateById(exists);
        log.info("[Sla] 更新 SLA 规则: slaId={} jobId={}", id, exists.getJobId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSla(String id) {
        JobSlaDO exists = jobSlaMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_sla_not_found");
        }
        jobSlaMapper.deleteById(id);
        log.info("[Sla] 删除 SLA 规则: slaId={} jobId={}", id, exists.getJobId());
    }

    @Override
    public JobSlaDO getSlaById(String id) {
        JobSlaDO sla = jobSlaMapper.selectById(id);
        if (sla == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_sla_not_found");
        }
        return sla;
    }

    @Override
    public List<JobSlaDO> listSla() {
        return jobSlaMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleSla(String id, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_sla_invalid_enabled");
        }
        JobSlaDO exists = jobSlaMapper.selectById(id);
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_sla_not_found");
        }
        exists.setEnabled(enabled);
        jobSlaMapper.updateById(exists);
        log.info("[Sla] 切换 SLA 启用状态: slaId={} enabled={}", id, enabled);
    }

    @Override
    public List<SlaViolation> checkViolation(String jobId) {
        List<SlaViolation> violations = new ArrayList<>();
        if (jobId == null || jobId.isBlank()) {
            return violations;
        }
        JobSlaDO sla = jobSlaMapper.selectByJobId(jobId);
        if (sla == null || sla.getEnabled() == null || sla.getEnabled() != 1) {
            return violations;
        }

        int windowMinutes = resolveWindowMinutes();
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        Map<String, Object> stats = jobLogMapper.countByJobIdSince(jobId, since);
        if (stats == null) {
            return violations;
        }
        long total = toLong(stats.get("total"));
        long failed = toLong(stats.get("failed"));
        if (total <= 0) {
            // 时间窗口内无执行记录，不触发违约
            return violations;
        }
        long success = total - failed;
        double failRate = (failed * 100.0) / total;
        double successRate = (success * 100.0) / total;

        // 检查最大执行时长（使用 P95 耗时作为实际值，更贴近尾延迟语义）
        if (sla.getMaxDurationMs() != null && sla.getMaxDurationMs() > 0) {
            Long p95Ms = jobLogMapper.selectDurationP95(jobId, since);
            if (p95Ms != null && p95Ms > sla.getMaxDurationMs()) {
                violations.add(new SlaViolation(
                        sla.getId(), sla.getJobId(), sla.getJobKey(),
                        "MAX_DURATION",
                        String.valueOf(p95Ms),
                        String.valueOf(sla.getMaxDurationMs()),
                        sla.getAlertLevel()));
            }
        }

        // 检查最大失败率
        if (sla.getMaxFailRate() != null) {
            BigDecimal maxRate = sla.getMaxFailRate();
            if (BigDecimal.valueOf(failRate).compareTo(maxRate) > 0) {
                violations.add(new SlaViolation(
                        sla.getId(), sla.getJobId(), sla.getJobKey(),
                        "FAIL_RATE",
                        String.format("%.2f", failRate),
                        maxRate.toPlainString(),
                        sla.getAlertLevel()));
            }
        }

        // 检查最小成功率
        if (sla.getMinSuccessRate() != null) {
            BigDecimal minRate = sla.getMinSuccessRate();
            if (BigDecimal.valueOf(successRate).compareTo(minRate) < 0) {
                violations.add(new SlaViolation(
                        sla.getId(), sla.getJobId(), sla.getJobKey(),
                        "SUCCESS_RATE",
                        String.format("%.2f", successRate),
                        minRate.toPlainString(),
                        sla.getAlertLevel()));
            }
        }
        return violations;
    }

    /**
     * 校验 SLA 约束（与 DDL CHECK 约束一致，提前在 Service 层拦截）。
     *
     * <p>约束：三个约束字段至少配置一项。
     */
    private void validateSlaConstraints(JobSlaSaveDTO dto) {
        if (dto.getMaxDurationMs() == null && dto.getMaxFailRate() == null
                && dto.getMinSuccessRate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_sla_no_constraint");
        }
        if (dto.getMaxDurationMs() != null && dto.getMaxDurationMs() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_sla_invalid_duration");
        }
        validateRateRange(dto.getMaxFailRate());
        validateRateRange(dto.getMinSuccessRate());
    }

    /**
     * 校验失败率/成功率范围 [0, 100]。
     */
    private void validateRateRange(BigDecimal rate) {
        if (rate == null) {
            return;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_sla_invalid_rate");
        }
    }

    /**
     * 将 DTO 字段应用到实体（创建/更新共用）。
     */
    private void applyDtoToEntity(JobSlaSaveDTO dto, JobSlaDO sla) {
        sla.setJobId(dto.getJobId());
        sla.setJobKey(dto.getJobKey());
        sla.setMaxDurationMs(dto.getMaxDurationMs());
        sla.setMaxFailRate(dto.getMaxFailRate());
        sla.setMinSuccessRate(dto.getMinSuccessRate());
        sla.setAlertLevel(StringUtils.hasText(dto.getAlertLevel()) ? dto.getAlertLevel() : "WARNING");
        sla.setEnabled(dto.getEnabled());
    }

    /**
     * 解析 SLA 检查时间窗口（分钟）。
     *
     * <p>当前固定使用 60 分钟默认窗口。后续可通过 CronjobProperties 扩展配置。
     */
    private int resolveWindowMinutes() {
        return DEFAULT_WINDOW_MINUTES;
    }

    /**
     * 安全将 Map 中的统计值转为 long（兼容 Number / String）。
     */
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
