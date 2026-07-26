package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 租户套餐菜单关联实体
 *
 * <p>套餐与菜单的多对多关系
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_tenant_plan_menu")
public class TenantPlanMenuDO extends MpBaseEntity<String> {

    /** 套餐 ID */
    private String planId;

    /** 菜单 ID */
    private String menuId;

    @TableLogic
    private Integer deleted;
}
