package com.njydsz.project.domain.dto.post;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 项目立项 新增请求 DTO。
 *
 * <p>创建场景：项目编号、名称、客户、类型为必填；
 * 预估金额、预算金额、计划日期等为可选。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectInitiationPostDTO {

    @NotBlank(message = "项目编号不能为空")
    @Size(max = 64, message = "项目编号长度不能超过64")
    private String projectCode;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称长度不能超过200")
    private String projectName;

    private String opportunityId;

    @NotBlank(message = "客户不能为空")
    @Size(max = 64, message = "客户ID长度不能超过64")
    private String customerId;

    @Size(max = 64, message = "业务部门ID长度不能超过64")
    private String businessDeptId;

    @NotBlank(message = "项目类型不能为空")
    @Size(max = 32, message = "项目类型长度不能超过32")
    private String projectType;

    @Size(max = 16, message = "项目等级长度不能超过16")
    private String projectLevel;

    @Size(max = 64, message = "项目经理ID长度不能超过64")
    private String pmId;

    @Size(max = 64, message = "发起人ID长度不能超过64")
    private String sponsorId;

    @DecimalMin(value = "0", message = "预估金额不能为负数")
    private BigDecimal estimatedAmount;

    @DecimalMin(value = "0", message = "预算金额不能为负数")
    private BigDecimal budgetAmount;

    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;

    @Size(max = 2000, message = "项目描述长度不能超过2000")
    private String description;

    @Size(max = 4000, message = "商业理由长度不能超过4000")
    private String businessCase;

    @Size(max = 4000, message = "风险评估长度不能超过4000")
    private String riskAssessment;
}
