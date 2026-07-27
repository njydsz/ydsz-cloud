package com.njydsz.project.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 项目立项 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectInitiationDTO {

    private String id;

    @NotBlank(message = "项目编号不能为空")
    private String projectCode;

    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    private String opportunityId;

    @NotBlank(message = "客户不能为空")
    private String customerId;

    private String businessDeptId;

    @NotBlank(message = "项目类型不能为空")
    private String projectType;

    private String projectLevel;

    private String pmId;
    private String sponsorId;

    private BigDecimal estimatedAmount;
    private BigDecimal budgetAmount;

    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;

    private String description;
    private String businessCase;
    private String riskAssessment;
}
