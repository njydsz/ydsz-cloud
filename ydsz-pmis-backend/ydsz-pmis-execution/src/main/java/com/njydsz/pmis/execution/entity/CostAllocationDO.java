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
import java.time.LocalDateTime;

/**
 * 成本归集
 */
@Data
@TableName("pmis_cost_allocation")
public class CostAllocationDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long initiationId;
    private String period;          // YYYY-MM
    private String costType;        // CostType.code
    private Long sourceId;
    private String sourceType;
    private String description;
    private BigDecimal amount;
    private Integer billable;
    private Integer allocated;
    private Long employeeId;
    private String employeeName;
    private String levelCode;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
