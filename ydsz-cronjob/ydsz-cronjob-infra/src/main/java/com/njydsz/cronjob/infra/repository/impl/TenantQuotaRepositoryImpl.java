package com.njydsz.cronjob.infra.repository.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.TenantQuota;
import com.njydsz.cronjob.infra.mapper.job.TenantQuotaMapper;
import com.njydsz.cronjob.infra.repository.TenantQuotaRepository;

/**
 * 租户配额 Repository 实现。
 *
 * <p>委托 {@link TenantQuotaMapper} 执行数据库操作，封装所有数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TenantQuotaRepositoryImpl implements TenantQuotaRepository {

  private final TenantQuotaMapper tenantQuotaMapper;

  @Override
  public TenantQuota selectByTenantId(String tenantId) {
    return tenantQuotaMapper.selectByTenantId(tenantId);
  }
}
