package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.TenantPlanMenuMapper;

/**
 * 租户方案菜单仓储。
 *
 * <p>封装 TenantPlanMenuMapper，提供租户方案菜单数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TenantPlanMenuRepository {

  private final TenantPlanMenuMapper tenantPlanMenuMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 租户方案菜单 Mapper
   */
  public TenantPlanMenuMapper getTenantPlanMenuMapper() {
    return tenantPlanMenuMapper;
  }
}
