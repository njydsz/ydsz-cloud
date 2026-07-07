package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 项目编号 */
    private String projectCode;
    /** 项目名称 */
    private String projectName;
    /** 关联商机 ID */
    private String opportunityId;
    /** 客户 ID */
    private String customerId;
    /** 客户名称 */
    private String customerName;
    /** 业务部门 ID */
    private String businessDeptId;
    /** 项目类型（FIXED_PRICE/T&M/OUTSOURCING/PRODUCT） */
    private String projectType;
    /** 项目分级（A/B/C） */
    private String projectLevel;
    /** 项目经理 ID */
    private String pmId;
    /** 项目经理名称 */
    private String pmName;
    /** 发起人 ID */
    private String sponsorId;
    /** 发起人名称 */
    private String sponsorName;
    /** 预估金额 */
    private BigDecimal estimatedAmount;
    /** 预算金额 */
    private BigDecimal budgetAmount;
    /** 计划开始日期 */
    private LocalDate plannedStartDate;
    /** 计划结束日期 */
    private LocalDate plannedEndDate;
    /** 工期天数 */
    private Integer durationDays;
    /** 立项阶段（InitiationStage.code） */
    private String stage;
    /** 当前门径评审点（GateCode） */
    private String currentGate;
    /** 项目描述 */
    private String description;
    /** 商业案例 */
    private String businessCase;
    /** 风险评估 */
    private String riskAssessment;
    /** 自研工作流实例 ID */
    private String workflowId;
    /** 租户 ID */
    private String tenantId;

    /**
     * 乐观锁版本号（P1-12）
     *
     * <p>MyBatis-Plus 在 UPDATE 时自动 SET version = version + 1 并 WHERE version = #{原 version}。
     * 若记录已被其他事务修改，UPDATE 影响行数为 0，抛出 OptimisticLockerException。
     */
    @Version
    private Integer version;

    /** 创建人 ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
