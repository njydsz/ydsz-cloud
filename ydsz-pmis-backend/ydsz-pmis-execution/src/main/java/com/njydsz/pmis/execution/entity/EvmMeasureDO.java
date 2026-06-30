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
 * EVM 挣值测量记录
 *
 * <p>按 (项目 × WBS × 周期) 记录 PV/EV/AC 三量，并计算 CPI/SPI/EAC/VAC。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_evm_measure")
public class EvmMeasureDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long initiationId;
    private Long wbsTaskId;        // 可空：项目级度量
    private String period;         // YYYY-MM

    private BigDecimal pv;         // 计划值（Budgeted Cost of Work Scheduled）
    private BigDecimal ev;         // 挣值（Budgeted Cost of Work Performed）
    private BigDecimal ac;         // 实际成本（Actual Cost of Work Performed）
    private BigDecimal bac;        // 完工预算（Budget at Completion）

    private BigDecimal cpi;        // 成本绩效指数 = EV/AC
    private BigDecimal spi;        // 进度绩效指数 = EV/PV
    private BigDecimal cv;         // 成本偏差 = EV-AC
    private BigDecimal sv;         // 进度偏差 = EV-PV
    private BigDecimal eac;        // 完工估算 = BAC/CPI
    private BigDecimal vac;        // 完工偏差 = BAC-EAC
    private BigDecimal etc;        // 完工尚需 = EAC-AC
    private BigDecimal tcpi;       // 完工绩效指数 = (BAC-EV)/(BAC-AC)

    private String alertLevel;     // EvmAlertLevel
    private String alertReason;    // 预警原因

    private LocalDate measureDate;
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
