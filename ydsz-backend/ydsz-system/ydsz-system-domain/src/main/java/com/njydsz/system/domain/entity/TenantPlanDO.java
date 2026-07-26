package com.njydsz.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 租户套餐实体
 *
 * <p>定义不同租户的权限/功能集合
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@TableName("ydsz_tenant_plan")
public class TenantPlanDO {

    /** 主键 ID */
    @TableId
    private String id;

    /** 套餐编码（唯一） */
    private String planCode;

    /** 套餐名称 */
    private String planName;

    /** 套餐描述 */
    private String description;

    /** 套餐状态: ACTIVE 启用 / INACTIVE 停用 */
    private String status;

    /** 排序号 */
    private Integer sortOrder;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后修改人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 最后修改时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记: 0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
