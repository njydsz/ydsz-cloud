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
 * 利润测算版本
 *
 * <p>支持项目利润滚动预测、多版本对比（What-if 模拟）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_profit_simulation")
public class ProfitSimulationDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String simulationCode;     // 业务编号
    private String simulationName;     // 测算名称
    private Long initiationId;         // 关联项目
    private Integer version;           // 版本号 V1/V2/V3...
    private String scenarioType;       // BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM

    private BigDecimal contractAmount;     // 合同金额
    private BigDecimal externalRevenue;    // 对外报价测算收入
    private BigDecimal internalCost;       // 对内成本
    private BigDecimal expectedHours;      // 预计投入人时
    private BigDecimal blendedRate;        // 混合费率
    private BigDecimal grossProfit;        // 测算毛利
    private BigDecimal grossMargin;        // 测算毛利率
    private BigDecimal targetMargin;       // 目标毛利率

    private BigDecimal laborCost;
    private BigDecimal purchaseCost;
    private BigDecimal expenseCost;
    private BigDecimal outsourceCost;

    private String assumptions;        // 假设条件（JSON 文本）
    private String status;             // SimulationStatus
    private String approverName;
    private LocalDateTime approvedAt;
    private String remark;
    private Long applicantId;
    private String applicantName;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
