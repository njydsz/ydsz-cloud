package com.njydsz.cronjob.infra.repository;

import com.njydsz.cronjob.infra.entity.job.TenantQuota;

/**
 * 租户配额 Repository。
 *
 * <p>封装 {@code ydsz_tenant_quota} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TenantQuotaRepository {

  /**
   * 根据租户 ID 查询配额记录。
   *
   * @param tenantId 租户 ID
   * @return 配额记录，不存在时返回 null
   */
  TenantQuota selectByTenantId(String tenantId);
}
