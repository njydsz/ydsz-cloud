package com.njydsz.project.domain.dto.put;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 项目立项 更新请求 DTO。
 *
 * <p>更新场景：id 为必填；其余字段可选更新（部分字段更新语义）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectInitiationPutDTO {

    @NotBlank(message = "ID不能为空")
    private String id;

    @Size(max = 64, message = "项目编号长度不能超过64")
    private String projectCode;

    @Size(max = 200, message = "项目名称长度不能超过200")
    private String projectName;

    private String opportunityId;

    @Size(max = 64, message = "客户ID长度不能超过64")
    private String customerId;

    @Size(max = 64, message = "业务部门ID长度不能超过64")
    private String businessDeptId;

    @Size(max = 32, message = "项目类型长度不能超过32")
    private String projectType;

    @Size(max = 16, message = "项目等级长度不能超过16")
    private String projectLevel;

    @Size(max = 64, message = "项目经理ID长度不能超过64")
    private String pmId;

    @Size(max = 64, message = "发起人ID长度不能超过64")
    private String sponsorId;

    private BigDecimal estimatedAmount;
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
