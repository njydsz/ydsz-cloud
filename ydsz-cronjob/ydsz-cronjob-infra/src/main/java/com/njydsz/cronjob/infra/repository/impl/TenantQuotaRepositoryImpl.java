package com.njydsz.cronjob.infra.repository.impl;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.repository.TenantQuotaRepository;
import com.njydsz.cronjob.domain.vo.TenantQuotaVO;
import com.njydsz.cronjob.infra.mapper.job.TenantQuotaMapper;

/**
 * 租户配额 Repository 实现（Infra 层）。
 *
 * @author ydsz-team
 * @since 26.09.01
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
}
