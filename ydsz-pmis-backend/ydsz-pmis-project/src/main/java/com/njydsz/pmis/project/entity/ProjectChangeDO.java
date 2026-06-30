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
 * 项目变更主表
 *
 * <p>覆盖 5 类变更：SCOPE/COST/CONTRACT/STAFF/SCHEDULE。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_change")
public class ProjectChangeDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String changeCode;
    private Long initiationId;
    private String changeType;        // ChangeType.code
    private String changeTitle;
    private String changeReason;
    private String changeDesc;

    // 影响评估字段
    private BigDecimal budgetImpact;       // 预算影响（正=增加，负=减少）
    private BigDecimal contractImpact;      // 合同金额影响
    private Integer scheduleImpactDays;    // 进度影响天数
    private BigDecimal profitImpact;        // 利润影响
    private BigDecimal profitImpactPct;     // 利润影响百分比（-1~1）
    private String riskLevelAfter;          // 变更后风险等级 LOW/MEDIUM/HIGH
    private Integer affectedWbsCount;       // 影响的 WBS 任务数
    private Integer affectedStaffCount;     // 影响的人员数

    // 重大变更标识（事业部总经理+财务总监双审批）
    private Integer majorFlag;
    private String approverRoles;           // JSON: ["GM","CFO"]

    private Long applicantId;
    private String applicantName;
    private Long contractId;                // 关联合同（可选）
    private String workflowId;              // 关联流程实例 ID
    private String status;                  // ChangeStatus.code
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime executedAt;
    private String remark;
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
