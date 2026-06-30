package com.njydsz.pmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资源分配 DTO
 */
@Data
public class ResourceAssignmentCreateDTO {

    @NotBlank(message = "分配编号不能为空")
    private String assignmentCode;

    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    private String employeeName;
    private String levelCode;

    private Long poolId;
    private String poolType;        // 冗余

    private Long initiationId;
    private String initiationName;
    private Long opportunityId;

    /** 业务动作：RESERVE/START/TRANSFER/RELEASE/CANCEL */
    @NotBlank(message = "业务动作不能为空")
    private String action;

    private BigDecimal allocation;     // 0-1
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private Integer billable;
    private BigDecimal dailyHours;
}
