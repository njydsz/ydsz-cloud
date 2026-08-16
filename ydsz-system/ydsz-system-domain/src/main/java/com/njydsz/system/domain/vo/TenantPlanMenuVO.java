package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 租户套餐-菜单关联 VO
 *
 * <p>对应 {@code ydsz_tenant_plan_menu} 表的展示视图，记录套餐与菜单的关联关系。
 * 租户购买套餐后自动获得关联菜单的访问权限，是 RBAC 的「套餐级」权限分配。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.TenantPlanMenu 套餐-菜单关联实体
 */
@Data
@Schema(description = "套餐-菜单关联视图对象")
public class TenantPlanMenuVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "套餐 ID")
    private String planId;

    @Schema(description = "菜单 ID")
    private String menuId;
}
