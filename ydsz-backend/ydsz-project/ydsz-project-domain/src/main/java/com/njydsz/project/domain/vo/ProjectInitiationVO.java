package com.njydsz.project.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * ProjectInitiation 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectInitiationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String projectCode;
    private String projectName;
    private String opportunityId;
    private String customerId;
    private String customerName;
    private String businessDeptId;
    private String projectType;
    private String projectLevel;
    private String pmId;
    private String pmName;
    private String sponsorId;
    private String sponsorName;
    private BigDecimal estimatedAmount;
    private BigDecimal budgetAmount;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private Integer durationDays;
    private String stage;
    private String currentGate;
    private String description;
    private String businessCase;
    private String riskAssessment;
    private String status;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}