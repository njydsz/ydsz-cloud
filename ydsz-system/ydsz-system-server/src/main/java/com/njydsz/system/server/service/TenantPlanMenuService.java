package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.dto.TenantPlanMenuDTO;
import com.njydsz.system.domain.vo.TenantPlanMenuVO;

/**
 * 租户套餐-菜单关联 Service 接口
 *
 * <p>提供套餐与菜单关联关系的配置能力。 租户购买套餐后自动获得关联菜单的访问权限，是 RBAC 的「套餐级」权限分配。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>查询</b>：{@link #listByPlanId} — 查询指定套餐关联的菜单列表
 *   <li><b>批量更新</b>：{@link #updatePlanMenus} — 为套餐批量配置菜单权限（先删后插）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.system.infra.entity.TenantPlanMenu 套餐-菜单关联实体
 */
public interface TenantPlanMenuService {

  /**
   * 查询指定套餐关联的菜单列表
   *
   * @param planId 套餐 ID
   * @return 套餐-菜单关联列表
   */
  List<TenantPlanMenuVO> listByPlanId(String planId);

  /**
   * 为套餐批量配置菜单权限
   *
   * <p>执行逻辑：先删除该套餐的所有旧关联，再批量插入新的关联记录。 整个操作在事务内完成。
   *
   * @param dto 套餐-菜单关联 DTO
   */
  void updatePlanMenus(TenantPlanMenuDTO dto);
}
