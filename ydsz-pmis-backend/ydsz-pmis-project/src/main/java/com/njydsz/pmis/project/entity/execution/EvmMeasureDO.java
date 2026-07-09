package com.njydsz.pmis.project.entity.execution;

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

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 项目立项ID */
    private String initiationId;
    /** WBS 任务ID（可空：项目级度量） */
    private String wbsTaskId;
    /** 所属期间（YYYY-MM） */
    private String period;

    /** 计划值（Budgeted Cost of Work Scheduled） */
    private BigDecimal pv;
    /** 挣值（Budgeted Cost of Work Performed） */
    private BigDecimal ev;
    /** 实际成本（Actual Cost of Work Performed） */
    private BigDecimal ac;
    /** 完工预算（Budget at Completion） */
    private BigDecimal bac;

    /** 成本绩效指数 = EV/AC */
    private BigDecimal cpi;
    /** 进度绩效指数 = EV/PV */
    private BigDecimal spi;
    /** 成本偏差 = EV-AC */
    private BigDecimal cv;
    /** 进度偏差 = EV-PV */
    private BigDecimal sv;
    /** 完工估算 = BAC/CPI */
    private BigDecimal eac;
    /** 完工偏差 = BAC-EAC */
    private BigDecimal vac;
    /** 完工尚需 = EAC-AC */
    private BigDecimal etc;
    /** 完工绩效指数 = (BAC-EV)/(BAC-AC) */
    private BigDecimal tcpi;

    /** 预警等级：EvmAlertLevel.code */
    private String alertLevel;
    /** 预警原因 */
    private String alertReason;

    /** 度量日期 */
    private LocalDate measureDate;
    /** 备注 */
    private String remark;

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
