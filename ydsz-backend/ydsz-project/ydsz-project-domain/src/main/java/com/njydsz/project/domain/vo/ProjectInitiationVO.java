package com.njydsz.project.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目立项视图对象
 *
 * <p>用于 Controller 层返回项目立项全生命周期数据，屏蔽实体层审计字段细节。
 * 对应实体 {@link com.njydsz.project.domain.entity.project.ProjectInitiation}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectInitiationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** 项目编号（唯一业务编码） */
    private String projectCode;

    /** 项目名称 */
    private String projectName;

    /** 关联商机 ID */
    private String opportunityId;

    /** 客户 ID */
    private String customerId;

    /** 客户名称（冗余字段，通过 NameAssembler 解析填充） */
    private String customerName;

    /** 业务部门 ID */
    private String businessDeptId;

    /** 项目类型 */
    private String projectType;

    /** 项目等级（A/B/C/D） */
    private String projectLevel;

    /** 项目经理用户 ID */
    private String pmId;

    /** 项目经理名称（冗余字段） */
    private String pmName;

    /** 发起人用户 ID */
    private String sponsorId;

    /** 发起人名称（冗余字段） */
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

    /** 当前门径评审阶段 */
    private String currentGate;

    /** 项目描述 */
    private String description;

    /** 商业案例 */
    private String businessCase;

    /** 风险评估 */
    private String riskAssessment;

    /** 项目状态 */
    private String status;

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}