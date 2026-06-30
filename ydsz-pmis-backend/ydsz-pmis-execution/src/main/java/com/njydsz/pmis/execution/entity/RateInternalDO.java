package com.njydsz.pmis.execution.entity;

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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String rateCode;           // 业务编号
    private String levelCode;          // 职级 L1-L18
    private Long departmentId;         // 事业部/部门 ID
    private String departmentName;
    private String billingUnit;        // DAY/HOUR
    private BigDecimal costAmount;     // 内部成本
    private String currency;           // CNY
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String status;             // ACTIVE/INACTIVE
    private String remark;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
