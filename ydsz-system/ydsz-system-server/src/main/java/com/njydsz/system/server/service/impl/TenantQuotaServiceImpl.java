package com.njydsz.system.server.service.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.enums.QuotaType;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.vo.TenantPlanVO;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.server.service.TenantPlanService;
import com.njydsz.system.server.service.TenantQuotaService;
import com.njydsz.system.server.service.TenantService;

/**
 * 租户配额 Service 实现
 *
 * <p>提供 SaaS 多租户体系下的配额校验能力。 配额上限从 {@link TenantPlanVO#getQuotaJson()} 解析，
 * 当前使用量通过 {@link QuotaUsageRegistry} 回调获取。
 *
 * <p><b>配额校验流程：</b>
 *
 * <ol>
 *   <li>获取租户套餐的配额上限
 *   <li>获取当前使用量（通过注册的使用量提供者）
 *   <li>判断是否超出上限
 * </ol>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantQuotaServiceImpl implements TenantQuotaService {

  /** 租户 Service（用于查询租户信息） */
  private final TenantService tenantService;

  /** 租户套餐 Service（用于查询套餐信息） */
  private final TenantPlanService tenantPlanService;

  /** 配额使用量注册表（各模块注册自己的使用量统计回调） */
  private final QuotaUsageRegistry quotaUsageRegistry;

  @Override
  public void checkQuota(String tenantId, QuotaType quotaType, int requestAmount) {
    if (requestAmount <= 0) {
      return;
    }
    Integer limit = getQuotaLimit(tenantId, quotaType);
    if (limit == null) {
      // null 表示无限制
      return;
    }
    int currentUsage = getQuotaUsage(tenantId, quotaType);
    if (currentUsage + requestAmount > limit) {
      throw BusinessException.of(SystemExceptionCode.PARAM_ERROR)
          .data("reason", String.format(
              "配额不足: %s 当前使用 %d, 请求新增 %d, 上限 %d",
              quotaType.getDisplayName(), currentUsage, requestAmount, limit))
          .data("quotaType", quotaType.name())
          .data("currentUsage", currentUsage)
          .data("requestAmount", requestAmount)
          .data("limit", limit);
    }
  }

  @Override
  public Integer getQuotaLimit(String tenantId, QuotaType quotaType) {
    TenantVO tenant = tenantService.getById(tenantId);
    if (tenant == null) {
      log.warn("[TenantQuotaService] 租户不存在: {}", tenantId);
      return null;
    }
    return getQuotaLimitFromPlan(tenant.getPlanId(), quotaType);
  }

  @Override
  public int getQuotaUsage(String tenantId, QuotaType quotaType) {
    BiFunction<String, QuotaType, Integer> provider =
        quotaUsageRegistry.getProvider(quotaType);
    if (provider == null) {
      log.warn("[TenantQuotaService] 未找到配额使用量提供者: {}", quotaType);
      return 0;
    }
    try {
      Integer usage = provider.apply(tenantId, quotaType);
      return usage != null ? usage : 0;
    } catch (Exception e) {
      log.warn("[TenantQuotaService] 获取配额使用量失败: {}, error={}", quotaType, e.getMessage());
      return 0;
    }
  }

  @Override
  public boolean isUnlimited(String tenantId, QuotaType quotaType) {
    return getQuotaLimit(tenantId, quotaType) == null;
  }

  /**
   * 从套餐中获取配额上限。
   *
   * @param planId 套餐 ID
   * @param quotaType 配额类型
   * @return 配额上限；null 表示无限制
   */
  private Integer getQuotaLimitFromPlan(String planId, QuotaType quotaType) {
    if (planId == null || planId.isBlank()) {
      return null;
    }
    TenantPlanVO plan = tenantPlanService.getById(planId);
    if (plan == null || plan.getQuotaJson() == null || plan.getQuotaJson().isBlank()) {
      return null;
    }
    try {
      Map<String, Object> quotaMap = YdszJson.fromJson(plan.getQuotaJson(), Map.class);
      if (quotaMap == null || !quotaMap.containsKey(quotaType.getJsonKey())) {
        return null;
      }
      Object value = quotaMap.get(quotaType.getJsonKey());
      if (value == null) {
        return null;
      }
      if (value instanceof Number number) {
        return number.intValue();
      }
      return Integer.parseInt(value.toString());
    } catch (Exception e) {
      log.warn("[TenantQuotaService] 解析套餐配额失败: planId={}, quotaType={}, error={}",
          planId, quotaType, e.getMessage());
      return null;
    }
  }

  /**
   * 配额使用量注册表（内部类）。
   *
   * <p>各模块通过 {@link #registerProvider} 注册自己的使用量统计回调， 实现配额校验与使用量统计的解耦。
   */
  @Service
  public static class QuotaUsageRegistry {

    /** 配额类型 → 使用量提供者（tenantId, quotaType → 当前使用量），并发安全 */
    private final Map<QuotaType, BiFunction<String, QuotaType, Integer>> providers =
        new ConcurrentHashMap<>();

    /**
     * 注册配额使用量提供者。
     *
     * @param quotaType 配额类型
     * @param provider 使用量提供者回调
     */
    public void registerProvider(QuotaType quotaType,
        BiFunction<String, QuotaType, Integer> provider) {
      providers.put(quotaType, provider);
      log.info("[TenantQuotaService] 注册配额使用量提供者: {}", quotaType);
    }

    /**
     * 获取配额使用量提供者。
     *
     * @param quotaType 配额类型
     * @return 使用量提供者；未注册返回 null
     */
    public BiFunction<String, QuotaType, Integer> getProvider(QuotaType quotaType) {
      return providers.get(quotaType);
    }
  }
}
