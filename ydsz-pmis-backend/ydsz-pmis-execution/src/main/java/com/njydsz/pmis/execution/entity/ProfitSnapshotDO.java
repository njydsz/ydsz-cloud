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
 * 项目利润快照
 */
@Data
@TableName("pmis_profit_snapshot")
public class ProfitSnapshotDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long initiationId;
    private String period;
    private BigDecimal contractAmount;
    private BigDecimal recognizedRevenue;
    private BigDecimal billedAmount;
    private BigDecimal receivedAmount;
    private BigDecimal laborCost;
    private BigDecimal purchaseCost;
    private BigDecimal expenseCost;
    private BigDecimal outsourceCost;
    private BigDecimal allocationCost;
    private BigDecimal totalCost;
    private BigDecimal grossProfit;
    private BigDecimal grossMargin;
    private BigDecimal progressPct;
    private BigDecimal billableHours;
    private BigDecimal nonBillableHours;
    private LocalDateTime snapshotAt;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
