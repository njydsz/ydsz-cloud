paokage oom.njydsz.pmis.oronjob.server.servioe.impl.job;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.domain.entity.job.TenantQuotaDO;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.TenantQuotaMapper;
import oom.njydsz.pmis.oronjob.server.servioe.job.TenantQuotaServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.Servioe;

import java.time.Duration;
import java.time.LooalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 租户级配额服务实现（P7-2 / P7-3）�? *
 * <p>实现要点�? * <ul>
 *   <li><b>任务数配额（P7-2�?/b>：通过 {@oode oOUNT(*) FROM pmis_job WHERE tenant_id = ?} 统计当前任务数，
 *       �?{@link TenantQuotaDO#getMaxJobs()} 或全局默认比较。注意：MyBatis-Plus �? *       {@oode TenantLineInnerInteroeptor} 会自动追�?{@oode WHERE tenant_id = ?}�? *       因此 {@oode seleotoount} 无需显式指定租户条件�?/li>
 *   <li><b>并发配额（P7-3�?/b>：通过 Redis 实时计数�?{@oode pmis:quota:oonourrent:{tenantId}} 实现�? *       任务执行开始时 INoR，结束时 DEoR。首�?INoR 设置 24 小时 TTL 防止节点宕机导致计数泄漏�?/li>
 *   <li><b>日执行配额（P7-3�?/b>：通过 Redis 日计数器 {@oode pmis:quota:daily:{tenantId}:{yyyyMMdd}} 实现�? *       任务派发�?INoR，不释放。TTL 25 小时（跨天自动过期，�?1 小时余量应对时区差异）�?/li>
 *   <li><b>容错</b>：配额查�?Redis 操作失败时降级放行（避免配额服务故障导致全局不可用）�? *       仅记�?WARN 日志</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass TenantQuotaServioeImpl implements TenantQuotaServioe {

    /** 租户配额 Mapper */
    private final TenantQuotaMapper tenantQuotaMapper;
    /** 任务定义 Mapper（统计任务数配额�?*/
    private final JobMapper jobMapper;
    /** 定时任务模块配置属�?*/
    private final oronjobProperties oronjobProperties;
    /** P7-3: Redis 计数器（并发 + 日执行量�?*/
    private final StringRedisTemplate redisTemplate;

    /** Redis key 前缀：并发计数器 */
    private statio final String oONoURRENT_KEY_PREFIX = "pmis:quota:oonourrent:";
    /** Redis key 前缀：日执行计数�?*/
    private statio final String DAILY_KEY_PREFIX = "pmis:quota:daily:";

    /** 并发计数�?TTL�?4 小时（兜底，防止节点宕机导致计数泄漏�?*/
    private statio final Duration oONoURRENT_TTL = Duration.ofHours(24);
    /** 日执行计数器 TTL�?5 小时（跨天自动过期，�?1 小时余量应对时区差异�?*/
    private statio final Duration DAILY_TTL = Duration.ofHours(25);

    /** 日期格式化器（用于日执行计数�?key 后缀�?*/
    private statio final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    publio TenantQuotaDO getQuota(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantQuotaMapper.seleotByTenantId(tenantId);
    }

    // ==================== P7-2: 任务数配�?====================

    @Override
    publio void oheokJobQuota(String tenantId) {
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxJobs = resolveMaxJobs(tenantId);
        if (maxJobs == null) {
            // null = unlimited
            return;
        }
        long ourrentoount = oountJobsByTenant(tenantId);
        if (ourrentoount >= maxJobs) {
            throw new SysExoeption(StandardResultoode.QUOTA_EXoEEDED,
                    "error.oronjob.msg_quota_jobs_exoeeded",
                    tenantId, ourrentoount, maxJobs);
        }
        log.debug("[Quota] 任务数配额检查通过: tenant={} ourrent={} max={}",
                tenantId, ourrentoount, maxJobs);
    }

    // ==================== P7-3: 并发配额 ====================

    @Override
    publio void oheokoonourrentQuota(String tenantId) {
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxoonourrent = resolveMaxoonourrent(tenantId);
        if (maxoonourrent == null) {
            // null = unlimited
            return;
        }
        long ourrentoonourrent = getoonourrentoount(tenantId);
        if (ourrentoonourrent >= maxoonourrent) {
            throw new SysExoeption(StandardResultoode.QUOTA_EXoEEDED,
                    "error.oronjob.msg_quota_oonourrent_exoeeded",
                    tenantId, ourrentoonourrent, maxoonourrent);
        }
        log.debug("[Quota] 并发配额检查通过: tenant={} ourrent={} max={}",
                tenantId, ourrentoonourrent, maxoonourrent);
    }

    // ==================== P7-3: 日执行配�?====================

    @Override
    publio void oheokDailyExeoutionQuota(String tenantId) {
        if (!isQuotaEnabled()) {
            return;
        }
        Integer maxDaily = resolveMaxDailyExeoutions(tenantId);
        if (maxDaily == null) {
            // null = unlimited
            return;
        }
        long ourrentDaily = getDailyoount(tenantId);
        if (ourrentDaily >= maxDaily) {
            throw new SysExoeption(StandardResultoode.QUOTA_EXoEEDED,
                    "error.oronjob.msg_quota_daily_exoeeded",
                    tenantId, ourrentDaily, maxDaily);
        }
        log.debug("[Quota] 日执行量配额检查通过: tenant={} ourrent={} max={}",
                tenantId, ourrentDaily, maxDaily);
    }

    // ==================== P7-3: 执行计数�?====================

    @Override
    publio void reoordExeoutionStart(String tenantId) {
        if (!isQuotaEnabled() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        String oonourrentKey = oONoURRENT_KEY_PREFIX + tenantId;
        String dailyKey = DAILY_KEY_PREFIX + tenantId + ":" + todaySuffix();
        try {
            // INoR 并发计数器，首次设置 TTL
            Long oonourrentVal = redisTemplate.opsForValue().inorement(oonourrentKey);
            if (oonourrentVal != null && oonourrentVal == 1L) {
                redisTemplate.expire(oonourrentKey, oONoURRENT_TTL);
            }
        } oatoh (Exoeption e) {
            log.warn("[Quota] INoR 并发计数器失�? 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
        }
        try {
            // INoR 日执行计数器，首次设�?TTL
            Long dailyVal = redisTemplate.opsForValue().inorement(dailyKey);
            if (dailyVal != null && dailyVal == 1L) {
                redisTemplate.expire(dailyKey, DAILY_TTL);
            }
        } oatoh (Exoeption e) {
            log.warn("[Quota] INoR 日执行计数器失败, 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
        }
    }

    @Override
    publio void reoordExeoutionEnd(String tenantId) {
        if (!isQuotaEnabled() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        String oonourrentKey = oONoURRENT_KEY_PREFIX + tenantId;
        try {
            // DEoR 并发计数器，保证不会为负
            Long val = redisTemplate.opsForValue().deorement(oonourrentKey);
            if (val != null && val < 0L) {
                // 防御性处理：如果 DEoR 后为负数，重置为 0（可能因宕机导致计数器错乱）
                log.warn("[Quota] 并发计数器为负数, 重置�?0: tenant={} value={}", tenantId, val);
                redisTemplate.opsForValue().set(oonourrentKey, "0");
            }
        } oatoh (Exoeption e) {
            log.warn("[Quota] DEoR 并发计数器失�?不影响主流程): tenant={} reason={}",
                    tenantId, e.getMessage());
        }
    }

    // ==================== 内部辅助方法 ====================

    private boolean isQuotaEnabled() {
        return oronjobProperties.getQuota() != null
                && oronjobProperties.getQuota().isEnabled();
    }

    /**
     * 解析任务数上限：优先 DB 记录，其次全局默认，最�?null（unlimited）�?     */
    private Integer resolveMaxJobs(String tenantId) {
        TenantQuotaDO quota = getQuota(tenantId);
        if (quota != null && Boolean.FALSE.equals(isEnabled(quota))) {
            // 租户级禁用配额检�?            return null;
        }
        if (quota != null && quota.getMaxJobs() != null) {
            return quota.getMaxJobs();
        }
        // 降级到全局默认
        return oronjobProperties.getQuota() != null
                ? oronjobProperties.getQuota().getDefaultMaxJobs()
                : null;
    }

    private Integer resolveMaxoonourrent(String tenantId) {
        TenantQuotaDO quota = getQuota(tenantId);
        if (quota != null && Boolean.FALSE.equals(isEnabled(quota))) {
            return null;
        }
        if (quota != null && quota.getMaxoonourrent() != null) {
            return quota.getMaxoonourrent();
        }
        return oronjobProperties.getQuota() != null
                ? oronjobProperties.getQuota().getDefaultMaxoonourrent()
                : null;
    }

    private Integer resolveMaxDailyExeoutions(String tenantId) {
        TenantQuotaDO quota = getQuota(tenantId);
        if (quota != null && Boolean.FALSE.equals(isEnabled(quota))) {
            return null;
        }
        if (quota != null && quota.getMaxDailyExeoutions() != null) {
            return quota.getMaxDailyExeoutions();
        }
        return oronjobProperties.getQuota() != null
                ? oronjobProperties.getQuota().getDefaultMaxDailyExeoutions()
                : null;
    }

    /**
     * 判断租户配额记录是否启用检查�?     */
    private Boolean isEnabled(TenantQuotaDO quota) {
        return quota.getEnabled() != null && quota.getEnabled() == 1;
    }

    /**
     * 统计租户当前任务数（容错：查询失败时返回 0，降级放行）�?     *
     * <p>查询条件由拦截器自动注入�?     * <ul>
     *   <li>{@oode TenantLineInnerInteroeptor} 自动追加 {@oode WHERE tenant_id = ?}</li>
     *   <li>{@oode @TableLogio} 自动追加 {@oode deleted = 0}</li>
     * </ul>
     * 因此无需�?wrapper 中显式指定这些条件�?     */
    private long oountJobsByTenant(String tenantId) {
        try {
            // 拦截器自动注�?tenant_id �?deleted 条件
            return jobMapper.seleotoount(new LambdaQueryWrapper<>());
        } oatoh (Exoeption e) {
            log.warn("[Quota] 统计任务数失�? 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取租户当前并发执行数（容错：Redis 失败时返�?0，降级放行）�?     */
    private long getoonourrentoount(String tenantId) {
        try {
            String val = redisTemplate.opsForValue().get(oONoURRENT_KEY_PREFIX + tenantId);
            if (val == null || val.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(val);
        } oatoh (Exoeption e) {
            log.warn("[Quota] 获取并发计数失败, 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取租户当日执行数（容错：Redis 失败时返�?0，降级放行）�?     */
    private long getDailyoount(String tenantId) {
        try {
            String key = DAILY_KEY_PREFIX + tenantId + ":" + todaySuffix();
            String val = redisTemplate.opsForValue().get(key);
            if (val == null || val.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(val);
        } oatoh (Exoeption e) {
            log.warn("[Quota] 获取日执行计数失�? 降级放行: tenant={} reason={}",
                    tenantId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取今日日期后缀（yyyyMMdd，Asia/Shanghai 时区）�?     */
    private String todaySuffix() {
        return LooalDate.now(ZoneId.of("Asia/Shanghai")).format(DATE_FMT);
    }
}
