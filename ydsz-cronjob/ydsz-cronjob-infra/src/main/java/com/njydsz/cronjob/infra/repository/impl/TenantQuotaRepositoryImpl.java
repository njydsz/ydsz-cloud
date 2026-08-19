package com.njydsz.cronjob.infra.repository.impl;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.job.TenantQuota;
import com.njydsz.cronjob.domain.repository.TenantQuotaRepository;
import com.njydsz.cronjob.domain.vo.TenantQuotaVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.mapper.job.TenantQuotaMapper;

/**
 * 租户配额 Repository 实现（Infra 层）。
 *
 * <p>实现 {@link TenantQuotaRepository} 接口，封装 TenantQuotaMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TenantQuotaRepositoryImpl implements TenantQuotaRepository {

  private final TenantQuotaMapper tenantQuotaMapper;

  private final CronjobConverter converter;

  @Override
  public Optional<TenantQuotaVO> findByTenantId(String tenantId) {
    return Optional.ofNullable(tenantQuotaMapper.selectByTenantId(tenantId))
        .map(converter::entityToVO);
  }

  @Override
  public TenantQuota selectByTenantId(String tenantId) {
    return tenantQuotaMapper.selectByTenantId(tenantId);
  }
}
