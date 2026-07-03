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
import java.time.LocalDateTime;

/**
 * 项目利润快照
 *
 * <p>按项目/期间定期生成利润快照，记录收入/成本/毛利等核心指标。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_profit_snapshot")
public class ProfitSnapshotDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目立项ID */
    private Long initiationId;
    /** 所属期间（YYYY-MM） */
    private String period;
    /** 合同金额 */
    private BigDecimal contractAmount;
    /** 已确认收入 */
    private BigDecimal recognizedRevenue;
    /** 已开票金额 */
    private BigDecimal billedAmount;
    /** 已回款金额 */
    private BigDecimal receivedAmount;
    /** 人力成本 */
    private BigDecimal laborCost;
    /** 采购成本 */
    private BigDecimal purchaseCost;
    /** 费用成本 */
    private BigDecimal expenseCost;
    /** 外包成本 */
    private BigDecimal outsourceCost;
    /** 分摊成本 */
    private BigDecimal allocationCost;
    /** 总成本 */
    private BigDecimal totalCost;
    /** 毛利润 */
    private BigDecimal grossProfit;
    /** 毛利率（0-1） */
    private BigDecimal grossMargin;
    /** 进度百分比（0-100） */
    private BigDecimal progressPct;
    /** 可计费工时 */
    private BigDecimal billableHours;
    /** 不可计费工时 */
    private BigDecimal nonBillableHours;
    /** 快照生成时间 */
    private LocalDateTime snapshotAt;
    /** 租户ID */
    private Long tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
