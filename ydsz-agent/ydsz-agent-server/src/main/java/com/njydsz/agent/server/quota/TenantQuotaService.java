package com.njydsz.agent.server.quota;

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

import lombok.extern.slf4j.Slf4j;

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
 * @since 1.0.0
 */
@Slf4j
@Service
public class TenantQuotaService {


  /** 每日配额 Key 前缀 */
  private static final String DAILY_KEY_PREFIX = "agent:quota:daily:";

  /** 月度配额 Key 前缀 */
  private static final String MONTHLY_KEY_PREFIX = "agent:quota:monthly:";

  /** 日期格式化 */
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** 月份格式化 */
  private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

  private final RedisStringOps redisStringOps;

  /** 本地降级缓存（Redis 不可用时使用） */
  private final ConcurrentMap<String, AtomicLong> localFallback = new ConcurrentHashMap<>();

  public TenantQuotaService(RedisStringOps redisStringOps) {
    this.redisStringOps = redisStringOps;
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
    String key = buildDailyKey(tenantId != null ? tenantId : "default");
    try {
      Long val = redisStringOps.get(key, Long.class);
      return val != null ? val : 0L;
    } catch (Exception e) {
      log.debug("[Quota] Redis 获取每日 Token 计数失败，降级本地缓存: {}", e.getMessage());
      return localFallback.getOrDefault(key, new AtomicLong(0)).get();
    }
  }

  /**
   * 获取租户本月已用成本（USD）。
   *
   * @param tenantId 租户 ID
   * @return 本月已用成本（USD）
   */
  public double getMonthlyCostUsd(String tenantId) {
    String key = buildMonthlyKey(tenantId != null ? tenantId : "default");
    try {
      Double val = redisStringOps.get(key, Double.class);
      return val != null ? val : 0.0;
    } catch (Exception e) {
      log.debug("[Quota] Redis 获取月度成本失败，降级本地缓存: {}", e.getMessage());
      return localFallback.getOrDefault(key, new AtomicLong(0)).get() / 10000.0;
    }
  }

  // ======================== 内部方法 ========================

  private long incrementDailyTokens(String tenantId, long delta) {
    String key = buildDailyKey(tenantId);
    try {
      return redisStringOps.incr(key, delta);
    } catch (Exception e) {
      log.warn("[Quota] Redis INCR 失败，降级本地缓存: key={}, delta={}", key, delta);
      return localFallback.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(delta);
    }
  }

  private double incrementMonthlyCost(String tenantId, double deltaUsd) {
    String key = buildMonthlyKey(tenantId);
    try {
      return redisStringOps.incrByFloat(key, deltaUsd);
    } catch (Exception e) {
      log.warn("[Quota] Redis INCR BY FLOAT 失败，降级本地缓存: key={}, delta={}", key, deltaUsd);
      // 本地缓存以微美元为单位存储（double -> long 转换）
      long microUsd = Math.round(deltaUsd * 10000);
      return localFallback.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(microUsd) / 10000.0;
    }
  }

  private static String buildDailyKey(String tenantId) {
    return DAILY_KEY_PREFIX + tenantId + ":" + LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DATE_FMT);
  }

  private static String buildMonthlyKey(String tenantId) {
    return MONTHLY_KEY_PREFIX + tenantId + ":" + YearMonth.now(ZoneId.of("Asia/Shanghai")).format(MONTH_FMT);
  }
}
