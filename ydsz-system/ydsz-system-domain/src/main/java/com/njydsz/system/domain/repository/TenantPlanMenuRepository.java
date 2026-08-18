package com.njydsz.system.domain.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.njydsz.system.infra.entity.TenantPlanMenu;

/**
 * 租户套餐-菜单关联仓储接口（Infra 层契约）。
 *
 * <p>定义套餐-菜单关联数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域实体（{@link TenantPlanMenu}），非 DTO / VO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TenantPlanMenuRepository {

  /**
   * 按条件查询套餐-菜单关联列表。
   *
   * @param wrapper 查询条件
   * @return 套餐-菜单关联列表
   */
  List<TenantPlanMenu> findList(LambdaQueryWrapper<TenantPlanMenu> wrapper);

  /**
   * 按条件删除套餐-菜单关联。
   *
   * @param wrapper 删除条件
   * @return 删除成功返回 {@code true}
   */
  boolean deleteByCondition(LambdaQueryWrapper<TenantPlanMenu> wrapper);

  /**
   * 批量插入套餐-菜单关联（一次 SQL 批量写入）。
   *
   * @param entities 套餐-菜单关联实体列表
   * @return 插入成功返回 {@code true}
   */
  boolean insertBatch(List<TenantPlanMenu> entities);
}
