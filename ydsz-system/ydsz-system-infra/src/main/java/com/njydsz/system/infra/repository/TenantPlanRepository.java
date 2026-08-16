package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.TenantPlanMapper;

/**
 * 租户方案仓储。
 *
 * <p>封装 TenantPlanMapper，提供租户方案数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TenantPlanRepository {

  private final TenantPlanMapper tenantPlanMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 租户方案 Mapper
   */
  public TenantPlanMapper getTenantPlanMapper() {
    return tenantPlanMapper;
  }
}
