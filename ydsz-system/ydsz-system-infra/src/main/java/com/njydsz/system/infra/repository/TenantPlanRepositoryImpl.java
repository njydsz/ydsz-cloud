package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.entity.TenantPlan;
import com.njydsz.system.infra.mapper.TenantPlanMapper;
import com.njydsz.system.domain.repository.TenantPlanRepository;

/**
 * 租户方案仓储实现（Infra 层）。
 *
 * <p>实现 {@link TenantPlanRepository} 接口，封装 {@link TenantPlanMapper} 数据访问细节。
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
public class TenantPlanRepositoryImpl implements TenantPlanRepository {

  private final TenantPlanMapper tenantPlanMapper;

  @Override
  public Optional<TenantPlan> findById(String id) {
    return Optional.ofNullable(tenantPlanMapper.selectById(id));
  }

  @Override
  public IPage<TenantPlan> findByPage(Page<TenantPlan> page, String planName, String status) {
    LambdaQueryWrapper<TenantPlan> wrapper = new LambdaQueryWrapper<>();
    if (planName != null && !planName.isBlank()) {
      wrapper.like(TenantPlan::getPlanName, planName);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq(TenantPlan::getStatus, status);
    }
    wrapper.orderByAsc(TenantPlan::getSortOrder);
    return tenantPlanMapper.selectPage(page, wrapper);
  }

  @Override
  public List<TenantPlan> findList(LambdaQueryWrapper<TenantPlan> wrapper) {
    return tenantPlanMapper.selectList(wrapper);
  }

  @Override
  public long countByCondition(LambdaQueryWrapper<TenantPlan> wrapper) {
    Long count = tenantPlanMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public boolean insert(TenantPlan entity) {
    return tenantPlanMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(TenantPlan entity) {
    return tenantPlanMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return tenantPlanMapper.deleteById(id) > 0;
  }
}
