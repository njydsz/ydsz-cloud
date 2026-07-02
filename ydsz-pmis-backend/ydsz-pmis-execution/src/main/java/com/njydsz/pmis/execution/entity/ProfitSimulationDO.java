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

    /** 业务编号 */
    private String simulationCode;
    /** 测算名称 */
    private String simulationName;
    /** 关联项目立项ID */
    private Long initiationId;
    /** 版本号 V1/V2/V3... */
    private Integer version;
    /** 场景类型：BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM */
    private String scenarioType;

    /** 合同金额 */
    private BigDecimal contractAmount;
    /** 对外报价测算收入 */
    private BigDecimal externalRevenue;
    /** 对内成本 */
    private BigDecimal internalCost;
    /** 预计投入人时 */
    private BigDecimal expectedHours;
    /** 混合费率 */
    private BigDecimal blendedRate;
    /** 测算毛利 */
    private BigDecimal grossProfit;
    /** 测算毛利率 */
    private BigDecimal grossMargin;
    /** 目标毛利率 */
    private BigDecimal targetMargin;

    /** 人力成本 */
    private BigDecimal laborCost;
    /** 采购成本 */
    private BigDecimal purchaseCost;
    /** 费用成本 */
    private BigDecimal expenseCost;
    /** 外包成本 */
    private BigDecimal outsourceCost;

    /** 假设条件（JSON 文本） */
    private String assumptions;
    /** 状态：SimulationStatus.code */
    private String status;
    /** 审批人姓名 */
    private String approverName;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 备注 */
    private String remark;
    /** 申请人ID */
    private Long applicantId;
    /** 申请人姓名 */
    private String applicantName;
    /** 租户ID */
    private Long tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
