package com.njydsz.pmis.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 项目变更创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ProjectChangeCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 变更编号 */
    @NotBlank(message = "变更编号不能为空")
    private String changeCode;

    /** 立项 ID */
    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    /** 变更类型（ChangeType.code） */
    @NotBlank(message = "变更类型不能为空")
    private String changeType;

    /** 变更标题 */
    @NotBlank(message = "变更标题不能为空")
    private String changeTitle;

    /** 变更原因 */
    private String changeReason;
    /** 变更描述 */
    private String changeDesc;
    /** 预算影响（正=增加，负=减少） */
    private BigDecimal budgetImpact;
    /** 合同金额影响 */
    private BigDecimal contractImpact;
    /** 进度影响天数 */
    private Integer scheduleImpactDays;
    /** 利润影响 */
    private BigDecimal profitImpact;
    /** 影响的 WBS 任务数 */
    private Integer affectedWbsCount;
    /** 影响的人员数 */
    private Integer affectedStaffCount;
    /** 关联合同 ID（可选） */
    private Long contractId;
    /** 申请人 ID */
    private Long applicantId;
    /** 申请人名称 */
    private String applicantName;
    /** 状态（ChangeStatus.code） */
    private String status;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private Long tenantId;
}
