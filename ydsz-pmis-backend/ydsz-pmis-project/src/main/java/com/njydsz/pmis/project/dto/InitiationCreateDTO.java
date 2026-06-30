package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 立项申请 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "立项申请")
public class InitiationCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "项目编号", required = true)
    private String projectCode;

    @NotBlank
    @Schema(description = "项目名称", required = true)
    private String projectName;

    @Schema(description = "来源商机 ID")
    private Long opportunityId;

    @NotNull
    @Schema(description = "客户 ID", required = true)
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "业务部门 ID")
    private Long businessDeptId;

    @NotBlank
    @Schema(description = "项目类型: FIXED_PRICE/T&M/OUTSOURCING/PRODUCT", required = true)
    private String projectType;

    @Schema(description = "项目级别 A/B/C", example = "C")
    private String projectLevel;

    @Schema(description = "项目经理 ID")
    private Long pmId;

    @Schema(description = "项目经理姓名")
    private String pmName;

    @Schema(description = "项目发起人 ID")
    private Long sponsorId;

    @Schema(description = "项目发起人姓名")
    private String sponsorName;

    @Schema(description = "预估金额")
    private BigDecimal estimatedAmount;

    @Schema(description = "预算金额")
    private BigDecimal budgetAmount;

    @Schema(description = "计划开始日期")
    private LocalDate plannedStartDate;

    @Schema(description = "计划结束日期")
    private LocalDate plannedEndDate;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "立项依据")
    private String businessCase;

    @Schema(description = "风险评估")
    private String riskAssessment;
}
