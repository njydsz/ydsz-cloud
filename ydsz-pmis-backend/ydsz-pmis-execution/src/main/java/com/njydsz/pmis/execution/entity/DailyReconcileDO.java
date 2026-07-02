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
 * 每日对账（P4-3）
 *
 * <p>按 (date, type, initiationId) 唯一；每天自动跑一次成本/收入/回款/开票/利润
 * 与上游业务账的差异校验，落库为差异记录。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_reconcile_daily")
public class DailyReconcileDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate reconcileDate;
    /** 维度：COST/REVENUE/PAYMENT/INVOICE/PROFIT/LABOR */
    private String reconcileType;
    private Long initiationId;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal diffAmount;
    private BigDecimal diffPct;
    /** OK / WARN / ERROR */
    private String status;
    /** 差异说明 / 明细 */
    private String detail;
    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
