package com.njydsz.project.domain.entity.project;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 项目立项主表实体。
 *
 * <p>对应数据库表 {@code ydsz_project_initiation}，承载项目立项全生命周期数据。
 * 项目立项是 PMIS 核心业务流程的第一步，记录项目基本信息、预算、时间计划及阶段门审信息。
 *
 * <p><b>核心流程：</b>
 * <ul>
 *   <li>发起立项申请 → 流程审批 → 立项通过 → 进入履约阶段</li>
 *   <li>门径评审（{@link ProjectGateReview}）与立项信息联动，按阶段推进</li>
 *   <li>关联合同（{@link ProjectContract}）、预算明细（{@link ProjectBudgetItem}）、费用（{@link ProjectExpense}）</li>
 * </ul>
 *
 * <p><b>核心字段：</b>
 * <ul>
 *   <li>{@code projectCode} / {@code projectName}：项目标识，全局唯一引用</li>
 *   <li>{@code pmId} / {@code pmName}：项目经理（冗余存储避免 JOIN）</li>
 *   <li>{@code stage} / {@code currentGate}：项目阶段与当前门审阶段，驱动流转</li>
 *   <li>{@code estimatedAmount} / {@code budgetAmount}：预估与预算金额</li>
 *   <li>{@code plannedStartDate} / {@code plannedEndDate} / {@code durationDays}：时间计划</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectGateReview 门径评审
 * @see ProjectContract 合同主表
 * @see ProjectBudgetItem 立项预算明细
 * @see ProjectExpense 项目费用
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("ydsz_project_initiation")
public class ProjectInitiation extends MpBaseEntity<String> {


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

    /** 实际开始日期 */
    private LocalDate actualStartDate;

    /** 实际结束日期 */
    private LocalDate actualEndDate;

}
