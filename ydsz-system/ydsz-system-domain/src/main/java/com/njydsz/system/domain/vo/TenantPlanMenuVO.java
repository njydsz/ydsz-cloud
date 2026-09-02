package com.njydsz.system.domain.vo;

import lombok.Data;

/**
 * 租户套餐-菜单关联 VO
 *
 * <p>对应 {@code ydsz_sys_tenant_plan_menu} 表的展示视图，记录套餐与菜单的关联关系。 租户购买套餐后自动获得关联菜单的访问权限，是 RBAC 的「套餐级」权限分配。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.system.domain.entity.TenantPlanMenu 套餐-菜单关联实体
 */
@Data
public class TenantPlanMenuVO {

  private String id;

  private String planId;

  private String menuId;
}
