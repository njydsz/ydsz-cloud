package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.Tenant;
import com.njydsz.system.infra.mapper.TenantMapper;
import com.njydsz.system.infra.repository.TenantRepository;

/**
 * 租户仓储实现（Infra 层）。
 *
 * <p>实现 {@link TenantRepository} 接口，封装 {@link TenantMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>返回领域实体，由 Service 层负责转换为 VO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TenantRepositoryImpl implements TenantRepository {

  private final TenantMapper tenantMapper;

  @Override
  public Optional<Tenant> findById(String id) {
    return Optional.ofNullable(tenantMapper.selectById(id));
  }

  @Override
  public IPage<Tenant> findByPage(Page<Tenant> page, String tenantName, String status) {
    LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
    if (tenantName != null && !tenantName.isBlank()) {
      wrapper.like(Tenant::getTenantName, tenantName);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(Tenant::getStatus, status);
    }
    wrapper.orderByDesc(Tenant::getCreatedAt);
    return tenantMapper.selectPage(page, wrapper);
  }

  @Override
  public long countByCondition(LambdaQueryWrapper<Tenant> wrapper) {
    Long count = tenantMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public boolean insert(Tenant entity) {
    return tenantMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(Tenant entity) {
    return tenantMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return tenantMapper.deleteById(id) > 0;
  }

  @Override
  public int disableExpiredTenants() {
    return tenantMapper.disableExpiredTenants();
  }
}
