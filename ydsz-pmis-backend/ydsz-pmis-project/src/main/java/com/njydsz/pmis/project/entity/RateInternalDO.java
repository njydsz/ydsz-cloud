package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对内成本费率
 *
 * <p>按 (职级 × 事业部) 维度定义内部核算成本。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_rate_internal")
public class RateInternalDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编号 */
    private String rateCode;
    /** 职级 L1-L18 */
    private String levelCode;
    /** 事业部/部门 ID */
    private Long departmentId;
    /** 部门名称 */
    private String departmentName;
    /** 计费单位：DAY/HOUR */
    private String billingUnit;
    /** 内部成本金额 */
    private BigDecimal costAmount;
    /** 币种：CNY */
    private String currency;
    /** 生效日期 */
    private LocalDate effectiveDate;
    /** 失效日期 */
    private LocalDate expiryDate;
    /** 状态：ACTIVE/INACTIVE */
    private String status;
    /** 备注 */
    private String remark;
    /** 租户ID */
    private Long tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
