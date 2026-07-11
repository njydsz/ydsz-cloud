package com.njydsz.pmis.sales.entity.closure;

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

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 结项业务编号 */
    private String closureCode;
    /** 项目立项ID */
    private String initiationId;
    /** 结项类型：ClosureType.code */
    private String closureType;
    /** 结项原因 */
    private String closureReason;

    // 准入指标
    /** 合同总额 */
    private BigDecimal contractAmount;
    /** 已回款金额 */
    private BigDecimal receivedAmount;
    /** 回款比例（0-1） */
    private BigDecimal receivedRatio;
    /** CPI（成本绩效指数） */
    private BigDecimal cpi;
    /** SPI（进度绩效指数） */
    private BigDecimal spi;
    /** 当前毛利率 */
    private BigDecimal grossMargin;
    /** 当前进度（0-100） */
    private BigDecimal progressPct;
    /** 累计成本 */
    private BigDecimal totalCost;
    /** 质保期月数 */
    private BigDecimal warrantyMonths;
    /** 质保期开始日期 */
    private LocalDate warrantyStartDate;
    /** 质保期结束日期 */
    private LocalDate warrantyEndDate;

    // 归档信息
    /** 计划归档日期 */
    private LocalDate plannedArchiveDate;
    /** 实际归档日期 */
    private LocalDate actualArchiveDate;
    /** 归档文件 ID 列表（JSON） */
    private String archiveFileIds;
    /** 是否锁定（归档后不可改）：1 是 / 0 否 */
    private Integer locked;
    /** 状态：ClosureStatus.code */
    private String status;
    /** 备注 */
    private String remark;

    // 审批
    /** 申请人ID */
    private String applicantId;
    /** 申请人姓名 */
    private String applicantName;
    /** 审批人ID */
    private String approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 提交时间 */
    private LocalDateTime submittedAt;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 归档时间 */
    private LocalDateTime archivedAt;
    /** 审批意见 */
    private String approvalComment;

    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
