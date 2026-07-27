package com.njydsz.project.domain.entity.project;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目立项主表 DO。
 *
 * <p>对应数据库表 {@code ydsz_project_initiation}，承载项目立项全生命周期数据。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_initiation")
public class ProjectInitiationDO extends MpBaseEntity<String> {


    /** 项目编号 */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 商机 ID */
    private String opportunityId;

    /** 客户 ID */
    private String customerId;

    /** 客户名称（冗余，通过 NameAssembler 解析） */
    private String customerName;

    /** 业务部门 ID */
    private String businessDeptId;

    /** 项目类型 */
    private String projectType;

    /** 项目等级（A/B/C/D） */
    private String projectLevel;

    /** 项目经理 ID */
    private String pmId;

    /** 项目经理名称（冗余） */
    private String pmName;

    /** 发起人 ID */
    private String sponsorId;

    /** 发起人名称（冗余） */
    private String sponsorName;

    /** 预估金额 */
    private BigDecimal estimatedAmount;

    /** 预算金额 */
    private BigDecimal budgetAmount;

    /** 计划开始日期 */
    private LocalDate plannedStartDate;

    /** 计划结束日期 */
    private LocalDate plannedEndDate;

    /** 预计工期（天） */
    private Integer durationDays;

    /** 项目阶段 */
    private String stage;

    /** 当前门审阶段 */
    private String currentGate;

    /** 项目描述 */
    private String description;

    /** 商业案例 */
    private String businessCase;

    /** 风险评估 */
    private String riskAssessment;

    /** 项目状态（DRAFT/APPROVED/IN_PROGRESS/CLOSED/CANCELLED） */
    private String status;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

}
