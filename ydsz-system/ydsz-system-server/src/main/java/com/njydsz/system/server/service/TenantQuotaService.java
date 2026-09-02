package com.njydsz.system.server.service;
import com.njydsz.system.domain.enums.QuotaType;


/**
 * 租户配额 Service 接口
 *
 * <p>提供 SaaS 多租户体系下的配额校验能力。 运行时由业务模块调用，校验当前租户是否超出套餐配额上限。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>{@link #checkQuota} — 校验配额是否充足
 *   <li>{@link #getQuotaLimit} — 获取配额上限
 *   <li>{@link #getQuotaUsage} — 获取当前使用量
 *   <li>{@link #isUnlimited} — 判断是否无限制
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface TenantQuotaService {

  /**
   * 校验当前租户的指定配额是否充足。
   *
   * <p>校验逻辑：
   *
   * <ol>
   *   <li>获取租户套餐的配额上限
   *   <li>获取当前使用量
   *   <li>判断是否超出上限
   * </ol>
   *
   * @param tenantId 租户 ID
   * @param quotaType 配额类型
   * @param requestAmount 请求增量（如新增用户数）
   * @throws com.njydsz.common.exception.custom.BusinessException 配额不足时抛出
   */
  void checkQuota(String tenantId, QuotaType quotaType, int requestAmount);

  /**
   * 获取租户套餐的配额上限。
   *
   * @param tenantId 租户 ID
   * @param quotaType 配额类型
   * @return 配额上限；返回 null 表示无限制
   */
  Integer getQuotaLimit(String tenantId, QuotaType quotaType);

  /**
   * 获取租户当前配额使用量。
   *
   * @param tenantId 租户 ID
   * @param quotaType 配额类型
   * @return 当前使用量
   */
  int getQuotaUsage(String tenantId, QuotaType quotaType);

  /**
   * 判断租户套餐的指定配额是否无限制。
   *
   * @param tenantId 租户 ID
   * @param quotaType 配额类型
   * @return true 为无限制
   */
  boolean isUnlimited(String tenantId, QuotaType quotaType);
}
