package com.njydsz.cronjob.server.service.impl.job;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.repository.TenantQuotaRepository;
import com.njydsz.cronjob.domain.vo.TenantQuotaVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.service.job.TenantQuotaService;

/**
 * 租户配额服务实现。
 *
 * <p>管理租户的任务配额 ({@code ydsz_tenant_quota})：并发任务数上限、日调度次数上限、
 *
 * <p>单租户 Worker 数量、跨租户任务隔离。
 *
 * <p>配额耗尽时拒绝任务提交并返回 429 Too Many Requests。
 *
 * @author ydsz-team
 * @since 1.0.0
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

  /** P7-3: Redis String 操作（并发 + 日执行量） */
  private final RedisStringOps redisStringOps;

  /** Redis key 前缀：并发计数器 */
  private static final String CONCURRENT_KEY_PREFIX = "ydsz:quota:concurrent:";

  /** Redis key 前缀：日执行计数器 */
  private static final String DAILY_KEY_PREFIX = "ydsz:quota:daily:";

  /** 并发计数器 TTL：24 小时（兜底，防止节点宕机导致计数泄漏） */
  private static final Duration CONCURRENT_TTL = Duration.ofHours(24);

  /** 日执行计数器 TTL：25 小时（跨天自动过期，留 1 小时余量应对时区差异） */
  private static final Duration DAILY_TTL = Duration.ofHours(25);

  /** 日期格式化器（用于日执行计数器 key 后缀） */
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

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
      // null = unlimited
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
      // null = unlimited
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
      // null = unlimited
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
      Long concurrentVal = redisStringOps.incr(concurrentKey, 1);
      if (concurrentVal != null && concurrentVal == 1L) {
        redisStringOps.expire(concurrentKey, CONCURRENT_TTL);
      }
    } catch (Exception e) {
      log.warn("[Quota] INCR 并发计数器失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
    }
    try {
      // INCR 日执行计数器，首次设置 TTL
      Long dailyVal = redisStringOps.incr(dailyKey, 1);
      if (dailyVal != null && dailyVal == 1L) {
        redisStringOps.expire(dailyKey, DAILY_TTL);
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
    String concurrentKey = CONCURRENT_KEY_PREFIX + tenantId;
    try {
      // DECR 并发计数器，保证不会为负
      long val = redisStringOps.decr(concurrentKey, 1);
      if (val < 0L) {
        // 防御性处理：如果 DECR 后为负数，重置为 0（可能因宕机导致计数器错乱）
        log.warn("[Quota] 并发计数器为负数, 重置为 0: tenant={} value={}", tenantId, val);
        redisStringOps.set(concurrentKey, "0");
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
      // 租户级禁用配额检查
      return null;
    }
    if (quota.isPresent() && quota.get().getMaxJobs() != null) {
      return quota.get().getMaxJobs();
    }
    // 降级到全局默认
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

  /** 判断租户配额记录是否启用检查。 */
  private Boolean isEnabled(TenantQuotaVO quota) {
    return quota.getEnabled() != null && quota.getEnabled() == 1;
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
      // 走 Repository 业务方法（拦截器自动注入 tenant_id 和 deleted 条件），避免直接使用 MyBatis Plus Wrapper 穿透 DDD 分层
      return jobRepository.countAll();
    } catch (Exception e) {
      log.warn("[Quota] 统计任务数失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
      return 0;
    }
  }

  /** 获取租户当前并发执行数（容错：Redis 失败时返回 0，降级放行）。 */
  private long getConcurrentCount(String tenantId) {
    try {
      String quotaValue = redisStringOps.get(CONCURRENT_KEY_PREFIX + tenantId, String.class);
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
      String key = DAILY_KEY_PREFIX + tenantId + ":" + todaySuffix();
      String quotaValue = redisStringOps.get(key, String.class);
      if (quotaValue == null || quotaValue.isEmpty()) {
        return 0L;
      }
      return Long.parseLong(quotaValue);
    } catch (Exception e) {
      log.warn("[Quota] 获取日执行计数失败, 降级放行: tenant={} reason={}", tenantId, e.getMessage());
      return 0L;
    }
  }

  /** 获取今日日期后缀（yyyyMMdd，Asia/Shanghai 时区）。 */
  private String todaySuffix() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DATE_FMT);
  }
}
