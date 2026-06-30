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

    @NotBlank(message = "变更编号不能为空")
    private String changeCode;

    @NotNull(message = "项目 ID 不能为空")
    private Long initiationId;

    @NotBlank(message = "变更类型不能为空")
    private String changeType;

    @NotBlank(message = "变更标题不能为空")
    private String changeTitle;

    private String changeReason;
    private String changeDesc;
    private BigDecimal budgetImpact;
    private BigDecimal contractImpact;
    private Integer scheduleImpactDays;
    private BigDecimal profitImpact;
    private Integer affectedWbsCount;
    private Integer affectedStaffCount;
    private Long contractId;
    private Long applicantId;
    private String applicantName;
    private String status;
    private String remark;
    private Long tenantId;
}
