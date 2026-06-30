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
 * 项目结项主表
 *
 * <p>支持 FORMAL（正式结项）/PRE_CLOSURE（预结项）/FORCED（强制结项）三种类型。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_execution_closure")
public class ProjectClosureDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String closureCode;
    private Long initiationId;
    private String closureType;        // ClosureType.code
    private String closureReason;

    // 准入指标
    private BigDecimal contractAmount;     // 合同总额
    private BigDecimal receivedAmount;     // 已回款
    private BigDecimal receivedRatio;      // 回款比例 0-1
    private BigDecimal cpi;                // CPI（成本绩效指数）
    private BigDecimal spi;                // SPI（进度绩效指数）
    private BigDecimal grossMargin;        // 当前毛利率
    private BigDecimal progressPct;        // 当前进度
    private BigDecimal totalCost;          // 累计成本
    private BigDecimal warrantyMonths;     // 质保期月数
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;

    // 归档信息
    private LocalDate plannedArchiveDate;
    private LocalDate actualArchiveDate;
    private String archiveFileIds;         // 归档文件 ID 列表（JSON）
    private Integer locked;                // 是否锁定（归档后不可改）
    private String status;                 // ClosureStatus.code
    private String remark;

    // 审批
    private Long applicantId;
    private String applicantName;
    private Long approverId;
    private String approverName;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime archivedAt;
    private String approvalComment;

    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
