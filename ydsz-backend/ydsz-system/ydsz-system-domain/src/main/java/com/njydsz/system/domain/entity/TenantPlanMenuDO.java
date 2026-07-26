package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 租户套餐菜单关联实体
 *
 * <p>套餐与菜单的多对多关系
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@TableName("ydsz_tenant_plan_menu")
public class TenantPlanMenuDO {

    /** 主键 ID */
    @TableId
    private String id;

    /** 套餐 ID */
    private String planId;

    /** 菜单 ID */
    private String menuId;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除标记: 0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
