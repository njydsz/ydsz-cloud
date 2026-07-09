package com.njydsz.pmis.project.entity.contract;

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
 * 合同变更记录
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_contract_change")
public class ContractChangeDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 合同 ID */
    private String contractId;
    /** 变更单号 */
    private String changeCode;
    /** 变更类型（SCOPE/AMOUNT/TERM/PERSONNEL/PROGRESS） */
    private String changeType;
    /** 变更原因 */
    private String changeReason;
    /** 变更前值 */
    private String beforeValue;
    /** 变更后值 */
    private String afterValue;
    /** 金额变动（正=增加，负=减少） */
    private BigDecimal amountDelta;
    /** 影响分析 */
    private String impactAnalysis;
    /** 状态（DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED） */
    private String status;
    /** 申请人 ID */
    private String applicantId;
    /** 申请人名称 */
    private String applicantName;
    /** 审批人 ID */
    private String approverId;
    /** 审批人名称 */
    private String approverName;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 自研工作流实例 ID */
    private String workflowId;
    /** 租户 ID */
    private String tenantId;

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
