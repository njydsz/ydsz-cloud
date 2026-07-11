package com.njydsz.pmis.finance.domain.entity;

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

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 对账日期 */
    private LocalDate reconcileDate;
    /** 维度：COST/REVENUE/PAYMENT/INVOICE/PROFIT/LABOR */
    private String reconcileType;
    /** 项目立项ID */
    private String initiationId;
    /** 期望金额（上游账应记金额） */
    private BigDecimal expectedAmount;
    /** 实际金额（业务账实记金额） */
    private BigDecimal actualAmount;
    /** 差异金额 */
    private BigDecimal diffAmount;
    /** 差异比例（0-1） */
    private BigDecimal diffPct;
    /** OK / WARN / ERROR */
    private String status;
    /** 差异说明 / 明细 */
    private String detail;
    /** 租户ID */
    private String tenantId;
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
