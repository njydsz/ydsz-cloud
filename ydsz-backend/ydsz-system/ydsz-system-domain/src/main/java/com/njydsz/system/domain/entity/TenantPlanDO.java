package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 租户套餐实体
 *
 * <p>定义不同租户的权限/功能集合
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_tenant_plan")
public class TenantPlanDO extends MpBaseEntity<String> {

    /** 套餐编码（唯一） */
    private String planCode;

    /** 套餐名称 */
    private String planName;

    /** 套餐描述 */
    private String description;

    /** 排序号 */
    private Integer sortOrder;

    @TableLogic
    private Integer deleted;
}
