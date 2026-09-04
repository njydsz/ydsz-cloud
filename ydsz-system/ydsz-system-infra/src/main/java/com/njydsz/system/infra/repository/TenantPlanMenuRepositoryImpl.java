package com.njydsz.system.infra.repository;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.dto.TenantPlanMenuDTO;
import com.njydsz.system.domain.repository.TenantPlanMenuRepository;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.entity.TenantPlanMenu;
import com.njydsz.system.infra.mapper.TenantPlanMenuMapper;




/**
 * 租户套餐-菜单关联仓储实现（Infra 层）。
 *
 * <p>实现 {@link TenantPlanMenuRepository} 接口，封装 {@link TenantPlanMenuMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link SystemConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link SystemConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class TenantPlanMenuRepositoryImpl implements TenantPlanMenuRepository {

  /** 默认初始容量 */
  private static final int DEFAULT_INITIAL_CAPACITY = 16;

  private final TenantPlanMenuMapper tenantPlanMenuMapper;

  private final SystemConverter converter;

  @Override
  public List<TenantPlanMenuVO> findByPlanId(String planId) {
    LambdaQueryWrapper<TenantPlanMenu> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TenantPlanMenu::getPlanId, planId);
    return converter.planMenuListToVO(tenantPlanMenuMapper.selectList(wrapper));
  }

  @Override
  public boolean deleteByPlanId(String planId) {
    LambdaQueryWrapper<TenantPlanMenu> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(TenantPlanMenu::getPlanId, planId);
    return tenantPlanMenuMapper.delete(wrapper) > 0;
  }

  @Override
  public boolean insertBatch(TenantPlanMenuDTO dto) {
    List<TenantPlanMenu> entities =
        new ArrayList<>(dto.getMenuIds() != null ? dto.getMenuIds().size() : DEFAULT_INITIAL_CAPACITY);
    if (dto.getMenuIds() != null) {
      for (String menuId : dto.getMenuIds()) {
        TenantPlanMenu entity = converter.dtoToEntity(dto.getPlanId(), menuId);
        entities.add(entity);
      }
    }
    return tenantPlanMenuMapper.insertBatch(entities) > 0;
  }
}
