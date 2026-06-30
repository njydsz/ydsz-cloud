package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 立项主表 DO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_project_initiation")
public class InitiationDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectCode;
    private String projectName;
    private Long opportunityId;
    private Long customerId;
    private String customerName;
    private Long businessDeptId;
    private String projectType;     // FIXED_PRICE/T&M/OUTSOURCING/PRODUCT
    private String projectLevel;    // A/B/C
    private Long pmId;
    private String pmName;
    private Long sponsorId;
    private String sponsorName;
    private BigDecimal estimatedAmount;
    private BigDecimal budgetAmount;
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private Integer durationDays;
    private String stage;            // InitiationStage.code
    private String currentGate;      // GateCode
    private String description;
    private String businessCase;
    private String riskAssessment;
    private String workflowId;
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
