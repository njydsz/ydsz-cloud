package com.njydsz.system.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.TenantPlanMenu;
import com.njydsz.system.infra.mapper.TenantPlanMenuMapper;
import com.njydsz.system.infra.repository.TenantPlanMenuRepository;

/**
 * 租户套餐-菜单关联仓储实现（Infra 层）。
 *
 * <p>实现 {@link TenantPlanMenuRepository} 接口，封装 {@link TenantPlanMenuMapper} 数据访问细节。
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
public class TenantPlanMenuRepositoryImpl implements TenantPlanMenuRepository {

  private final TenantPlanMenuMapper tenantPlanMenuMapper;

  @Override
  public List<TenantPlanMenu> findList(LambdaQueryWrapper<TenantPlanMenu> wrapper) {
    return tenantPlanMenuMapper.selectList(wrapper);
  }

  @Override
  public boolean deleteByCondition(LambdaQueryWrapper<TenantPlanMenu> wrapper) {
    return tenantPlanMenuMapper.delete(wrapper) > 0;
  }

  @Override
  public boolean insertBatch(List<TenantPlanMenu> entities) {
    return tenantPlanMenuMapper.insertBatch(entities) > 0;
  }
}
