package com.njydsz.pmis.project.entity.initiation;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 变更单号 */
    private String changeCode;
    /** 立项 ID */
    private String initiationId;
    /** 变更类型（ChangeType.code） */
    private String changeType;
    /** 变更标题 */
    private String changeTitle;
    /** 变更原因 */
    private String changeReason;
    /** 变更描述 */
    private String changeDesc;

    // 影响评估字段
    /** 预算影响（正=增加，负=减少） */
    private BigDecimal budgetImpact;
    /** 合同金额影响 */
    private BigDecimal contractImpact;
    /** 进度影响天数 */
    private Integer scheduleImpactDays;
    /** 利润影响 */
    private BigDecimal profitImpact;
    /** 利润影响百分比（-1~1） */
    private BigDecimal profitImpactPct;
    /** 变更后风险等级 LOW/MEDIUM/HIGH */
    private String riskLevelAfter;
    /** 影响的 WBS 任务数 */
    private Integer affectedWbsCount;
    /** 影响的人员数 */
    private Integer affectedStaffCount;

    // 重大变更标识（事业部总经理+财务总监双审批）
    /** 重大变更标识（0 否，1 是） */
    private Integer majorFlag;
    /** 审批角色 JSON 数组，例如 ["GM","CFO"] */
    private String approverRoles;

    /** 申请人 ID */
    private String applicantId;
    /** 申请人名称 */
    private String applicantName;
    /** 关联合同（可选） */
    private String contractId;
    /** 关联流程实例 ID */
    private String workflowId;
    /** 状态（ChangeStatus.code） */
    private String status;
    /** 提交时间 */
    private LocalDateTime submittedAt;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 执行时间 */
    private LocalDateTime executedAt;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private String tenantId;
    /** LLM Provider 跟踪 ID */
    private String providerTraceId;

    /** 乐观锁版本号（P1-12） */
    @Version
    private Integer version;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
