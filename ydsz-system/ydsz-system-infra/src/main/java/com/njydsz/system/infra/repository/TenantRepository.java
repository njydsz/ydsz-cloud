package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.TenantMapper;

/**
 * 租户仓储。
 *
 * <p>封装 TenantMapper，提供租户数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TenantRepository {

  private final TenantMapper tenantMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 租户 Mapper
   */
  public TenantMapper getTenantMapper() {
    return tenantMapper;
  }
}
