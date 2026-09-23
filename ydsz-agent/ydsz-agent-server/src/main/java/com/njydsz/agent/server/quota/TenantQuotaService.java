package com.njydsz.agent.server.quota;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.agent.domain.model.CostEstimate;
import com.njydsz.agent.domain.model.TenantQuota;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.safe.quota.QuotaCounter;

/**
 * 租户 LLM 配额管理服务
 *
 * <p>负责 LLM 调用前的配额预检与调用后的用量记录。 采用 Redis 原子 INCR 实现分布式精确计数，降级到本地内存计数器保证可用性。
 *
 * <p>Key 设计：
 *
 * <ul>
 *   <li>每日 Token 计数：{@code agent:quota:daily:{tenantId}:{yyyy-MM-dd}}
 *   <li>月度成本计数：{@code agent:quota:monthly:{tenantId}:{yyyy-MM}}
 * </ul>
 *
 * <p>TTL 策略：每日 Key 保留 48 小时，月度 Key 保留 35 天，覆盖跨时区边界。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class TenantQuotaService {


  /** 每日配额 Key 前缀 */
  private static final String DAILY_KEY_PREFIX = "agent:quota:daily:";

  /** 月度配额 Key 前缀 */
  private static final String MONTHLY_KEY_PREFIX = "agent:quota:monthly:";

  /** 每日 Key 保留 48 小时，覆盖跨时区边界 */
  private static final Duration DAILY_TTL = Duration.ofHours(48);

  /** 月度 Key 保留 35 天 */
  private static final Duration MONTHLY_TTL = Duration.ofDays(35);

  /** 每日 Token 计数器（带 TTL 自动过期 + 本地降级） */
  private final QuotaCounter dailyTokenCounter;

  /** 月度成本计数器（微美元单位，带 TTL 自动过期 + 本地降级） */
  private final QuotaCounter monthlyCostCounter;

  /** 本地降级缓存（保留用于微美元精度本地降级） */
  private final ConcurrentMap<String, AtomicLong> localFallback = new ConcurrentHashMap<>();

  public TenantQuotaService(RedisStringOps redisStringOps) {
    this.dailyTokenCounter = new QuotaCounter(redisStringOps, DAILY_KEY_PREFIX, DAILY_TTL);
    this.monthlyCostCounter = new QuotaCounter(redisStringOps, MONTHLY_KEY_PREFIX, MONTHLY_TTL);
  }

  /**
   * 调用前配额预检：检查租户是否还有足够的配额发起本次调用。
   *
   * <p>基于估算的 Token 数进行预检，若估算用量已经超过剩余配额则提前拒绝。
   *
   * @param tenantId 租户 ID
   * @param quota 租户配额配置
   * @param estimatedTokens 本次调用估算 Token 数
   * @param estimatedCostUsd 本次调用估算成本（USD）
   * @throws BusinessException 配额不足时抛出
   */
  public void preCheck(String tenantId, TenantQuota quota, int estimatedTokens, double estimatedCostUsd) {
    if (quota == null) {
      return;
    }
    String tid = tenantId != null ? tenantId : "default";
    if (quota.isDailyTokenLimited()) {
      long currentDaily = getDailyTokenCount(tid);
      if (currentDaily + estimatedTokens > quota.getDailyTokenLimit()) {
        log.warn("[Quota] 租户每日 Token 配额不足: tenant={}, current={}, estimated={}, limit={}",
            tid, currentDaily, estimatedTokens, quota.getDailyTokenLimit());
        // P1 修复：原引用不存在的 YdszException 类，改用 BusinessException + AgentExceptionCode
        throw BusinessException.builder()
            .resultCode(AgentExceptionCode.QUOTA_DAILY_TOKEN_EXCEEDED)
            .message(String.format(
                "每日 Token 配额不足（已用 %d + 预估 %d > 限额 %d）",
                currentDaily, estimatedTokens, quota.getDailyTokenLimit()))
            .build();
      }
    }
    if (quota.isMonthlyBudgetLimited()) {
      double currentMonthly = getMonthlyCostUsd(tid);
      if (currentMonthly + estimatedCostUsd > quota.getMonthlyBudgetUsd()) {
        log.warn("[Quota] 租户月度预算配额不足: tenant={}, current={}, estimated={}, limit={}",
            tid, currentMonthly, estimatedCostUsd, quota.getMonthlyBudgetUsd());
        throw BusinessException.builder()
            .resultCode(AgentExceptionCode.QUOTA_MONTHLY_BUDGET_EXCEEDED)
            .message(String.format(
                "月度预算配额不足（已用 %.4f + 预估 %.4f > 限额 %.2f USD）",
                currentMonthly, estimatedCostUsd, quota.getMonthlyBudgetUsd()))
            .build();
      }
    }
  }

  /**
   * 调用后用量记录：累加实际 Token 用量和成本。
   *
   * @param tenantId 租户 ID
   * @param costEstimate 实际成本核算结果
   */
  public void recordUsage(String tenantId, CostEstimate costEstimate) {
    if (costEstimate == null) {
      return;
    }
    String tid = tenantId != null ? tenantId : "default";
    int actualTokens = costEstimate.getActualTotalTokens();
    double actualCostUsd = costEstimate.getActualCostUsd();
    if (actualTokens > 0) {
      long newDaily = incrementDailyTokens(tid, actualTokens);
      log.info("[Quota] 记录每日 Token 用量: tenant={}, delta={}, newTotal={}", tid, actualTokens, newDaily);
    }
    if (actualCostUsd > 0) {
      double newMonthly = incrementMonthlyCost(tid, actualCostUsd);
      log.info("[Quota] 记录月度成本: tenant={}, delta={}, newTotal={}", tid, actualCostUsd, newMonthly);
    }
  }

  /**
   * 获取租户今日已用 Token 数。
   *
   * @param tenantId 租户 ID
   * @return 今日已用 Token 数
   */
  public long getDailyTokenCount(String tenantId) {
    return dailyTokenCounter.get(buildDateSuffix(tenantId));
  }

  /**
   * 获取租户本月已用成本（USD）。
   *
   * @param tenantId 租户 ID
   * @return 本月已用成本（USD）
   */
  public double getMonthlyCostUsd(String tenantId) {
    String suffix = buildMonthSuffix(tenantId != null ? tenantId : "default");
    // 微美元单位的 long 值转为 USD
    return monthlyCostCounter.get(suffix) / 10000.0;
  }

  // ======================== 内部方法 ========================

  private long incrementDailyTokens(String tenantId, long delta) {
    return dailyTokenCounter.incr(buildDateSuffix(tenantId), delta);
  }

  private double incrementMonthlyCost(String tenantId, double deltaUsd) {
    long microUsd = Math.round(deltaUsd * 10000);
    String suffix = buildMonthSuffix(tenantId);
    try {
      return monthlyCostCounter.incr(suffix, microUsd) / 10000.0;
    } catch (Exception e) {
      // QuotaCounter 内部降级异常后再次失败时，回退到本地缓存
      log.warn("[Quota] Redis 月度成本 INCR 双重失败，本地降级: tenantId={}, delta={}", tenantId, deltaUsd);
      return localFallback.computeIfAbsent(suffix, k -> new AtomicLong(0)).addAndGet(microUsd) / 10000.0;
    }
  }

  private static String buildDateSuffix(String tenantId) {
    return tenantId + ":" + LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DATE_FMT);
  }

  private static String buildMonthSuffix(String tenantId) {
    return tenantId + ":" + YearMonth.now(ZoneId.of("Asia/Shanghai")).format(MONTH_FMT);
  }
}
