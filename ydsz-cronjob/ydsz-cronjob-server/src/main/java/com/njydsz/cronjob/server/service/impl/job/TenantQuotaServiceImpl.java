package com.njydsz.cronjob.server.service.impl.job;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.util.date.DateUtils;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.repository.TenantQuotaRepository;
import com.njydsz.cronjob.domain.vo.TenantQuotaVO;
import com.njydsz.cronjob.server.cache.CacheKeyBuilder;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.redis.CronjobRedisOps;
import com.njydsz.cronjob.server.service.job.TenantQuotaService;

/**
 * 租户配额服务实现。
 *
 * <p>管理租户的任务配额 ({@code ydzs_job_tenant_quota})：并发任务数上限、日调度次数上限、
 * 单租户 Worker 数量、跨租户任务隔离。配额耗尽时拒绝任务提交并返回 429 Too Many Requests。
 *
 * <p><b>P2-1 整改（26.09.30）</b>：使用 {@link CacheKeyBuilder} 构造租户隔离的 Redis key，
 * 通过 {@link CronjobRedisOps} 执行操作，替代此前直接注入 {@code RedisStringOps} 并手写硬编码前缀
 * （{@code "ydsz:quota:concurrent:"} / {@code "ydsz:quota:daily:"}）的违规方式。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaServiceImpl implements TenantQuotaService {

  /** 租户配额 Repository */
  private final TenantQuotaRepository tenantQuotaRepository;

  /** 任务定义 Repository（统计任务数配额） */
  private final JobRepository jobRepository;

  /** 定时任务模块配置属性 */
  private final CronjobProperties cronjobProperties;

  /** P2-1 整改：模块级 Redis 操作收敛入口（封装 RedisStringOps + 统一异常降级） */
  private final CronjobRedisOps cronjobRedisOps;

  /** P2-1 整改：缓存键构造器（替代手写硬编码前缀） */
  private final CacheKeyBuilder cacheKeyBuilder;

  /** 并发计数器 TTL（小时），兜底防止节点宕机导致计数泄漏 */
  @Value("${ydsz.cronjob.quota.concurrent-ttl-hours:24}")
  private long concurrentTtlHours;

  /** 日执行计数器 TTL（小时），跨天自动过期，留余量应对时区差异 */
  @Value("${ydsz.cronjob.quota.daily-ttl-hours:25}")
  private long dailyTtlHours;

  @Override
  public TenantQuotaVO getQuota(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return null;
    }
    return tenantQuotaRepository.findByTenantId(tenantId).orElse(null);
  }

  // ==================== P7-2: 任务数配额 ====================

  @Override
  public void checkJobQuota(String tenantId) {
    if (!isQuotaEnabled()) {
      return;
    }
    Integer maxJobs = resolveMaxJobs(tenantId);
    if (maxJobs == null) {
      return;
    }
    long currentCount = countJobsByTenant(tenantId);
    if (currentCount >= maxJobs) {
      throw SysException.builder()
          .resultCode(YdszResultCode.TOO_MANY_REQUESTS)
          .key("error.cronjob.msg_quota_jobs_exceeded")
          .params(tenantId, currentCount, maxJobs)
          .build();
    }
    log.debug("[Quota] 任务数配额检查通过: tenant={} current={} max={}", tenantId, currentCount, maxJobs);
  }

  // ==================== P7-3: 并发配额 ====================

  @Override
  public void checkConcurrentQuota(String tenantId) {
    if (!isQuotaEnabled()) {
      return;
    }
    Integer maxConcurrent = resolveMaxConcurrent(tenantId);
    if (maxConcurrent == null) {
      return;
    }
    long currentConcurrent = getConcurrentCount(tenantId);
    if (currentConcurrent >= maxConcurrent) {
      throw SysException.builder()
          .resultCode(YdszResultCode.TOO_MANY_REQUESTS)
          .key("error.cronjob.msg_quota_concurrent_exceeded")
          .params(tenantId, currentConcurrent, maxConcurrent)
          .build();
    }
    log.debug(
        "[Quota] 并发配额检查通过: tenant={} current={} max={}",
        tenantId,
        currentConcurrent,
        maxConcurrent);
  }

  // ==================== P7-3: 日执行配额 ====================

  @Override
  public void checkDailyExecutionQuota(String tenantId) {
    if (!isQuotaEnabled()) {
      return;
    }
    Integer maxDaily = resolveMaxDailyExecutions(tenantId);
    if (maxDaily == null) {
      return;
    }
    long currentDaily = getDailyCount(tenantId);
    if (currentDaily >= maxDaily) {
      throw SysException.builder()
          .resultCode(YdszResultCode.TOO_MANY_REQUESTS)
          .key("error.cronjob.msg_quota_daily_exceeded")
          .params(tenantId, currentDaily, maxDaily)
          .build();
    }
    log.debug("[Quota] 日执行量配额检查通过: tenant={} current={} max={}", tenantId, currentDaily, maxDaily);
  }

  // ==================== P2-1 整改：执行计数器（使用 CronjobRedisOps + CacheKeyBuilder） ====================

  @Override
  public void recordExecutionStart(String tenantId) {
    if (!isQuotaEnabled() || tenantId == null || tenantId.isBlank()) {
      return;
    }
    // P2-1: 通过 CacheKeyBuilder 构造租户隔离 key
    String concurrentKey = cacheKeyBuilder.quotaConcurrent(tenantId);
    String dailyKey = cacheKeyBuilder.quotaDaily(tenantId, todaySuffix());
    try {
      Long concurrentVal = cronjobRedisOps.incrRaw(concurrentKey, 1);
      if (concurrentVal != null && concurrentVal == 1L) {
        cronjobRedisOps.expireRaw(concurrentKey, concurrentTtlHours * Duration.ofHours(1).getSeconds());
      }
    } catch (Exception e) {
      log.warn("[Quota] INCR 并发计数器失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
    }
    try {
      Long dailyVal = cronjobRedisOps.incrRaw(dailyKey, 1);
      if (dailyVal != null && dailyVal == 1L) {
        cronjobRedisOps.expireRaw(dailyKey, dailyTtlHours * Duration.ofHours(1).getSeconds());
      }
    } catch (Exception e) {
      log.warn("[Quota] INCR 日执行计数器失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
    }
  }

  @Override
  public void recordExecutionEnd(String tenantId) {
    if (!isQuotaEnabled() || tenantId == null || tenantId.isBlank()) {
      return;
    }
    String concurrentKey = cacheKeyBuilder.quotaConcurrent(tenantId);
    try {
      long val = cronjobRedisOps.decrRaw(concurrentKey, 1);
      if (val < 0L) {
        log.warn("[Quota] 并发计数器为负数, 重置为 0: tenant={} value={}", tenantId, val);
        cronjobRedisOps.setLongRaw(concurrentKey, 0L);
      }
    } catch (Exception e) {
      log.warn("[Quota] DECR 并发计数器失败(不影响主流程): tenant={} reason={}", tenantId, e.getMessage());
    }
  }

  // ==================== 内部辅助方法 ====================

  private boolean isQuotaEnabled() {
    return cronjobProperties.getQuota() != null && cronjobProperties.getQuota().isEnabled();
  }

  /** 解析任务数上限：优先 DB 记录，其次全局默认，最后 null（unlimited）。 */
  private Integer resolveMaxJobs(String tenantId) {
    Optional<TenantQuotaVO> quota = getQuotaOpt(tenantId);
    if (quota.isPresent() && Boolean.FALSE.equals(isEnabled(quota.get()))) {
      return null;
    }
    if (quota.isPresent() && quota.get().getMaxJobs() != null) {
      return quota.get().getMaxJobs();
    }
    return cronjobProperties.getQuota() != null
        ? cronjobProperties.getQuota().getDefaultMaxJobs()
        : null;
  }

  private Integer resolveMaxConcurrent(String tenantId) {
    Optional<TenantQuotaVO> quota = getQuotaOpt(tenantId);
    if (quota.isPresent() && Boolean.FALSE.equals(isEnabled(quota.get()))) {
      return null;
    }
    if (quota.isPresent() && quota.get().getMaxConcurrent() != null) {
      return quota.get().getMaxConcurrent();
    }
    return cronjobProperties.getQuota() != null
        ? cronjobProperties.getQuota().getDefaultMaxConcurrent()
        : null;
  }

  private Integer resolveMaxDailyExecutions(String tenantId) {
    Optional<TenantQuotaVO> quota = getQuotaOpt(tenantId);
    if (quota.isPresent() && Boolean.FALSE.equals(isEnabled(quota.get()))) {
      return null;
    }
    if (quota.isPresent() && quota.get().getMaxDailyExecutions() != null) {
      return quota.get().getMaxDailyExecutions();
    }
    return cronjobProperties.getQuota() != null
        ? cronjobProperties.getQuota().getDefaultMaxDailyExecutions()
        : null;
  }

  /**
   * 判断租户配额记录是否启用检查。
   *
   * <p>YDIZ-OOP-006 合规：VO {@code isEnabled} 已改为 primitive {@code boolean}，
   * 直接读取无需 null 判断。
   */
  private boolean isEnabled(TenantQuotaVO quota) {
    return quota.isEnabled();
  }

  private Optional<TenantQuotaVO> getQuotaOpt(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return Optional.empty();
    }
    return tenantQuotaRepository.findByTenantId(tenantId);
  }

  /**
   * 统计租户当前任务数（容错：查询失败时返回 0，降级放行）。
   *
   * <p>查询条件由拦截器自动注入：
   *
   * <ul>
   *   <li>{@code TenantLineInnerInterceptor} 自动追加 {@code WHERE tenant_id = ?}
   *   <li>{@code @TableLogic} 自动追加 {@code deleted = 0}
   * </ul>
   *
   * 因此无需在 wrapper 中显式指定这些条件。
   */
  private long countJobsByTenant(String tenantId) {
    try {
      return jobRepository.countAll();
    } catch (Exception e) {
      log.warn("[Quota] 统计任务数失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
      return 0;
    }
  }

  /** 获取租户当前并发执行数（容错：Redis 失败时返回 0，降级放行）。 */
  private long getConcurrentCount(String tenantId) {
    try {
      String quotaValue = cronjobRedisOps.get(cacheKeyBuilder.quotaConcurrent(tenantId), String.class);
      if (quotaValue == null || quotaValue.isEmpty()) {
        return 0L;
      }
      return Long.parseLong(quotaValue);
    } catch (Exception e) {
      log.warn("[Quota] 获取并发计数失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
      return 0L;
    }
  }

  /** 获取租户当日执行数（容错：Redis 失败时返回 0，降级放行）。 */
  private long getDailyCount(String tenantId) {
    try {
      String key = cacheKeyBuilder.quotaDaily(tenantId, todaySuffix());
      String quotaValue = cronjobRedisOps.get(key, String.class);
      if (quotaValue == null || quotaValue.isEmpty()) {
        return 0L;
      }
      return Long.parseLong(quotaValue);
    } catch (Exception e) {
      log.warn("[Quota] 获取日执行计数失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
      return 0L;
    }
  }

  /** 获取今日日期后缀（yyyyMMdd，Asia/Shanghai 时区）。YDIZ-COMMON-019 合规。 */
  private String todaySuffix() {
    return DateUtils.formatLocalDate(LocalDate.now(ZoneId.of("Asia/Shanghai")), "yyyyMMdd");
  }
}
