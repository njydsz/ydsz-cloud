package com.njydsz.pmis.cronjob.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.TenantQuotaDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.TenantQuotaMapper;
import com.njydsz.pmis.cronjob.service.TenantQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 租户级配额服务实现（P7-2）。
 *
 * <p>实现要点：
 * <ul>
 *   <li><b>任务数配额</b>：通过 {@code COUNT(*) FROM pmis_job WHERE tenant_id = ?} 统计当前任务数，
 *       与 {@link TenantQuotaDO#getMaxJobs()} 或全局默认比较。注意：MyBatis-Plus 的
 *       {@code TenantLineInnerInterceptor} 会自动追加 {@code WHERE tenant_id = ?}，
 *       因此 {@code selectCount} 无需显式指定租户条件。</li>
 *   <li><b>并发配额</b>：P7-3 通过 Redis 实时计数器实现（{@code pmis:quota:concurrent:{tenantId}}）</li>
 *   <li><b>日执行配额</b>：P7-3 通过 Redis 日计数器实现（{@code pmis:quota:daily:{tenantId}:{yyyyMMdd}}）</li>
 *   <li><b>容错</b>：配额查询失败时降级放行（避免配额服务故障导致全局不可用），
 *       仅记录 WARN 日志</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaServiceImpl implements TenantQuotaService {

    private final TenantQuotaMapper tenantQuotaMapper;
    private final JobMapper jobMapper;
    private final CronjobProperties cronjobProperties;

    @Override
    public TenantQuotaDO getQuota(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantQuotaMapper.selectByTenantId(tenantId);
    }

    @Override
    public void checkJobQuota(String tenantId) {
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxJobs = resolveMaxJobs(tenantId);
        if (maxJobs == null) {
            // null = unlimited
            return;
        }
        long currentCount = countJobsByTenant(tenantId);
        if (currentCount >= maxJobs) {
            throw new BizException(BizErrorCode.QUOTA_EXCEEDED,
                    "error.cronjob.msg_quota_jobs_exceeded",
                    tenantId, currentCount, maxJobs);
        }
        log.debug("[Quota] 任务数配额检查通过: tenant={} current={} max={}",
                tenantId, currentCount, maxJobs);
    }

    @Override
    public void checkConcurrentQuota(String tenantId) {
        // P7-3 实现：通过 Redis 实时计数器检查并发数
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxConcurrent = resolveMaxConcurrent(tenantId);
        if (maxConcurrent == null) {
            return;
        }
        // TODO P7-3: Redis INCR/DECR 实时并发计数器
        log.debug("[Quota] 并发配额检查（P7-3 实现）: tenant={} max={}", tenantId, maxConcurrent);
    }

    @Override
    public void checkDailyExecutionQuota(String tenantId) {
        // P7-3 实现：通过 Redis 日计数器检查日执行量
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxDaily = resolveMaxDailyExecutions(tenantId);
        if (maxDaily == null) {
            return;
        }
        // TODO P7-3: Redis INCR + TTL 日计数器
        log.debug("[Quota] 日执行量配额检查（P7-3 实现）: tenant={} max={}", tenantId, maxDaily);
    }

    // -------- 内部辅助方法 --------

    private boolean isQuotaEnabled() {
        return cronjobProperties.getQuota() != null
                && cronjobProperties.getQuota().isEnabled();
    }

    /**
     * 解析任务数上限：优先 DB 记录，其次全局默认，最后 null（unlimited）。
     */
    private Integer resolveMaxJobs(String tenantId) {
        TenantQuotaDO quota = getQuota(tenantId);
        if (quota != null && Boolean.FALSE.equals(isEnabled(quota))) {
            // 租户级禁用配额检查
            return null;
        }
        if (quota != null && quota.getMaxJobs() != null) {
            return quota.getMaxJobs();
        }
        // 降级到全局默认
        return cronjobProperties.getQuota() != null
                ? cronjobProperties.getQuota().getDefaultMaxJobs()
                : null;
    }

    private Integer resolveMaxConcurrent(String tenantId) {
        TenantQuotaDO quota = getQuota(tenantId);
        if (quota != null && Boolean.FALSE.equals(isEnabled(quota))) {
            return null;
        }
        if (quota != null && quota.getMaxConcurrent() != null) {
            return quota.getMaxConcurrent();
        }
        return cronjobProperties.getQuota() != null
                ? cronjobProperties.getQuota().getDefaultMaxConcurrent()
                : null;
    }

    private Integer resolveMaxDailyExecutions(String tenantId) {
        TenantQuotaDO quota = getQuota(tenantId);
        if (quota != null && Boolean.FALSE.equals(isEnabled(quota))) {
            return null;
        }
        if (quota != null && quota.getMaxDailyExecutions() != null) {
            return quota.getMaxDailyExecutions();
        }
        return cronjobProperties.getQuota() != null
                ? cronjobProperties.getQuota().getDefaultMaxDailyExecutions()
                : null;
    }

    /**
     * 判断租户配额记录是否启用检查。
     */
    private Boolean isEnabled(TenantQuotaDO quota) {
        return quota.getEnabled() != null && quota.getEnabled() == 1;
    }

    /**
     * 统计租户当前任务数（容错：查询失败时返回 0，降级放行）。
     *
     * <p>查询条件由拦截器自动注入：
     * <ul>
     *   <li>{@code TenantLineInnerInterceptor} 自动追加 {@code WHERE tenant_id = ?}</li>
     *   <li>{@code @TableLogic} 自动追加 {@code deleted = 0}</li>
     * </ul>
     * 因此无需在 wrapper 中显式指定这些条件。
     */
    private long countJobsByTenant(String tenantId) {
        try {
            // 拦截器自动注入 tenant_id 和 deleted 条件
            return jobMapper.selectCount(new LambdaQueryWrapper<>());
        } catch (Exception e) {
            log.warn("[Quota] 统计任务数失败, 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
            return 0;
        }
    }
}
