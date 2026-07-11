package com.njydsz.pmis.project.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 项目编号 */
    @NotBlank
    @Schema(description = "项目编号", requiredMode = RequiredMode.REQUIRED)
    private String projectCode;

    /** 项目名称 */
    @NotBlank
    @Schema(description = "项目名称", requiredMode = RequiredMode.REQUIRED)
    private String projectName;

    /** 来源商机 ID */
    @Schema(description = "来源商机 ID")
    private String opportunityId;

    /** 客户 ID */
    @NotNull
    @Schema(description = "客户 ID", requiredMode = RequiredMode.REQUIRED)
    private String customerId;

    /** 客户名称 */
    @Schema(description = "客户名称")
    private String customerName;

    /** 业务部门 ID */
    @Schema(description = "业务部门 ID")
    private String businessDeptId;

    /** 项目类型（FIXED_PRICE/T&M/OUTSOURCING/PRODUCT） */
    @NotBlank
    @Schema(description = "项目类型: FIXED_PRICE/T&M/OUTSOURCING/PRODUCT", requiredMode = RequiredMode.REQUIRED)
    private String projectType;

    /** 项目级别 A/B/C */
    @Schema(description = "项目级别 A/B/C", example = "C")
    private String projectLevel;

    /** 项目经理 ID */
    @Schema(description = "项目经理 ID")
    private String pmId;

    /** 项目经理姓名 */
    @Schema(description = "项目经理姓名")
    private String pmName;

    /** 项目发起人 ID */
    @Schema(description = "项目发起人 ID")
    private String sponsorId;

    /** 项目发起人姓名 */
    @Schema(description = "项目发起人姓名")
    private String sponsorName;

    /** 预估金额 */
    @Schema(description = "预估金额")
    private BigDecimal estimatedAmount;

    /** 预算金额 */
    @Schema(description = "预算金额")
    private BigDecimal budgetAmount;

    /** 计划开始日期 */
    @Schema(description = "计划开始日期")
    private LocalDate plannedStartDate;

    /** 计划结束日期 */
    @Schema(description = "计划结束日期")
    private LocalDate plannedEndDate;

    /** 项目描述 */
    @Schema(description = "项目描述")
    private String description;

    /** 立项依据 */
    @Schema(description = "立项依据")
    private String businessCase;

    /** 风险评估 */
    @Schema(description = "风险评估")
    private String riskAssessment;
}
