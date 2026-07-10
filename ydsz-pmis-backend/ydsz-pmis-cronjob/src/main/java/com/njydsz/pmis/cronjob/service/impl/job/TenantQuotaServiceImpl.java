package com.njydsz.pmis.cronjob.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.entity.job.TenantQuotaDO;
import com.njydsz.pmis.cronjob.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.mapper.job.TenantQuotaMapper;
import com.njydsz.pmis.cronjob.service.job.TenantQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 租户级配额服务实现（P7-2 / P7-3）。
 *
 * <p>实现要点：
 * <ul>
 *   <li><b>任务数配额（P7-2）</b>：通过 {@code COUNT(*) FROM pmis_job WHERE tenant_id = ?} 统计当前任务数，
 *       与 {@link TenantQuotaDO#getMaxJobs()} 或全局默认比较。注意：MyBatis-Plus 的
 *       {@code TenantLineInnerInterceptor} 会自动追加 {@code WHERE tenant_id = ?}，
 *       因此 {@code selectCount} 无需显式指定租户条件。</li>
 *   <li><b>并发配额（P7-3）</b>：通过 Redis 实时计数器 {@code pmis:quota:concurrent:{tenantId}} 实现，
 *       任务执行开始时 INCR，结束时 DECR。首次 INCR 设置 24 小时 TTL 防止节点宕机导致计数泄漏。</li>
 *   <li><b>日执行配额（P7-3）</b>：通过 Redis 日计数器 {@code pmis:quota:daily:{tenantId}:{yyyyMMdd}} 实现，
 *       任务派发时 INCR，不释放。TTL 25 小时（跨天自动过期，留 1 小时余量应对时区差异）。</li>
 *   <li><b>容错</b>：配额查询/Redis 操作失败时降级放行（避免配额服务故障导致全局不可用），
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

    /** 租户配额 Mapper */
    private final TenantQuotaMapper tenantQuotaMapper;
    /** 任务定义 Mapper（统计任务数配额） */
    private final JobMapper jobMapper;
    /** 定时任务模块配置属性 */
    private final CronjobProperties cronjobProperties;
    /** P7-3: Redis 计数器（并发 + 日执行量） */
    private final StringRedisTemplate redisTemplate;

    /** Redis key 前缀：并发计数器 */
    private static final String CONCURRENT_KEY_PREFIX = "pmis:quota:concurrent:";
    /** Redis key 前缀：日执行计数器 */
    private static final String DAILY_KEY_PREFIX = "pmis:quota:daily:";

    /** 并发计数器 TTL：24 小时（兜底，防止节点宕机导致计数泄漏） */
    private static final Duration CONCURRENT_TTL = Duration.ofHours(24);
    /** 日执行计数器 TTL：25 小时（跨天自动过期，留 1 小时余量应对时区差异） */
    private static final Duration DAILY_TTL = Duration.ofHours(25);

    /** 日期格式化器（用于日执行计数器 key 后缀） */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public TenantQuotaDO getQuota(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantQuotaMapper.selectByTenantId(tenantId);
    }

    // ==================== P7-2: 任务数配额 ====================

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

    // ==================== P7-3: 并发配额 ====================

    @Override
    public void checkConcurrentQuota(String tenantId) {
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxConcurrent = resolveMaxConcurrent(tenantId);
        if (maxConcurrent == null) {
            // null = unlimited
            return;
        }
        long currentConcurrent = getConcurrentCount(tenantId);
        if (currentConcurrent >= maxConcurrent) {
            throw new BizException(BizErrorCode.QUOTA_EXCEEDED,
                    "error.cronjob.msg_quota_concurrent_exceeded",
                    tenantId, currentConcurrent, maxConcurrent);
        }
        log.debug("[Quota] 并发配额检查通过: tenant={} current={} max={}",
                tenantId, currentConcurrent, maxConcurrent);
    }

    // ==================== P7-3: 日执行配额 ====================

    @Override
    public void checkDailyExecutionQuota(String tenantId) {
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxDaily = resolveMaxDailyExecutions(tenantId);
        if (maxDaily == null) {
            // null = unlimited
            return;
        }
        long currentDaily = getDailyCount(tenantId);
        if (currentDaily >= maxDaily) {
            throw new BizException(BizErrorCode.QUOTA_EXCEEDED,
                    "error.cronjob.msg_quota_daily_exceeded",
                    tenantId, currentDaily, maxDaily);
        }
        log.debug("[Quota] 日执行量配额检查通过: tenant={} current={} max={}",
                tenantId, currentDaily, maxDaily);
    }

    // ==================== P7-3: 执行计数器 ====================

    @Override
    public void recordExecutionStart(String tenantId) {
        if (!isQuotaEnabled() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        String concurrentKey = CONCURRENT_KEY_PREFIX + tenantId;
        String dailyKey = DAILY_KEY_PREFIX + tenantId + ":" + todaySuffix();
        try {
            // INCR 并发计数器，首次设置 TTL
            Long concurrentVal = redisTemplate.opsForValue().increment(concurrentKey);
            if (concurrentVal != null && concurrentVal == 1L) {
                redisTemplate.expire(concurrentKey, CONCURRENT_TTL);
            }
        } catch (Exception e) {
            log.warn("[Quota] INCR 并发计数器失败, 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
        }
        try {
            // INCR 日执行计数器，首次设置 TTL
            Long dailyVal = redisTemplate.opsForValue().increment(dailyKey);
            if (dailyVal != null && dailyVal == 1L) {
                redisTemplate.expire(dailyKey, DAILY_TTL);
            }
        } catch (Exception e) {
            log.warn("[Quota] INCR 日执行计数器失败, 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
        }
    }

    @Override
    public void recordExecutionEnd(String tenantId) {
        if (!isQuotaEnabled() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        String concurrentKey = CONCURRENT_KEY_PREFIX + tenantId;
        try {
            // DECR 并发计数器，保证不会为负
            Long val = redisTemplate.opsForValue().decrement(concurrentKey);
            if (val != null && val < 0L) {
                // 防御性处理：如果 DECR 后为负数，重置为 0（可能因宕机导致计数器错乱）
                log.warn("[Quota] 并发计数器为负数, 重置为 0: tenant={} value={}", tenantId, val);
                redisTemplate.opsForValue().set(concurrentKey, "0");
            }
        } catch (Exception e) {
            log.warn("[Quota] DECR 并发计数器失败(不影响主流程): tenant={} reason={}",
                    tenantId, e.getMessage());
        }
    }

    // ==================== 内部辅助方法 ====================

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

    /**
     * 获取租户当前并发执行数（容错：Redis 失败时返回 0，降级放行）。
     */
    private long getConcurrentCount(String tenantId) {
        try {
            String val = redisTemplate.opsForValue().get(CONCURRENT_KEY_PREFIX + tenantId);
            if (val == null || val.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(val);
        } catch (Exception e) {
            log.warn("[Quota] 获取并发计数失败, 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取租户当日执行数（容错：Redis 失败时返回 0，降级放行）。
     */
    private long getDailyCount(String tenantId) {
        try {
            String key = DAILY_KEY_PREFIX + tenantId + ":" + todaySuffix();
            String val = redisTemplate.opsForValue().get(key);
            if (val == null || val.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(val);
        } catch (Exception e) {
            log.warn("[Quota] 获取日执行计数失败, 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取今日日期后缀（yyyyMMdd，Asia/Shanghai 时区）。
     */
    private String todaySuffix() {
        return LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(DATE_FMT);
    }
}
