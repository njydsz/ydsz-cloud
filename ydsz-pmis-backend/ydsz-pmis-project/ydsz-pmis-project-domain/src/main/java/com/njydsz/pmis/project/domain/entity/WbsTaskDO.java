package com.njydsz.pmis.project.domain.entity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

/**
 * WBS 任务
 *
 * <p>项目工作分解结构（WBS）任务实体，支持任务/里程碑/汇总节点，
 * 记录计划与实际进度、责任人、依赖关系等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_execution_wbs_task")
public class WbsTaskDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务编号 */
    private String taskCode;
    /** 任务名称 */
    private String taskName;
    /** 项目立项ID */
    private String initiationId;
    /** 父任务ID（ null 表示根节点） */
    private String parentId;
    /** 任务层级（从 1 开始） */
    private Integer taskLevel;
    /** WBS 路径（如 1.2.3） */
    private String wbsPath;
    /** 同级排序号 */
    private Integer sortOrder;
    /** 任务类型：TASK/MILESTONE/SUMMARY */
    private String taskType;
    /** 计划开始日期 */
    private LocalDate plannedStartDate;
    /** 计划结束日期 */
    private LocalDate plannedEndDate;
    /** 实际开始日期 */
    private LocalDate actualStartDate;
    /** 实际结束日期 */
    private LocalDate actualEndDate;
    /** 工期（天） */
    private Integer durationDays;
    /** 计划工时（人天） */
    private BigDecimal plannedEffort;
    /** 实际工时（人天） */
    private BigDecimal actualEffort;
    /** 进度百分比（0-100） */
    private BigDecimal progressPct;
    /** 责任人ID */
    private String ownerId;
    /** 责任人姓名 */
    private String ownerName;
    /** 派单人员ID列表（逗号分隔） */
    private String assigneeIds;
    /** 优先级：LOW/NORMAL/HIGH/URGENT */
    private String priority;
    /** 状态：WbsTaskStatus.code */
    private String status;
    /** 前置依赖任务ID列表（逗号分隔） */
    private String dependsOn;
    /** 是否里程碑：1 是 / 0 否 */
    private Integer milestone;
    /** 任务描述 */
    private String description;
    /** 交付物说明 */
    private String deliverable;
    /** 风险等级 */
    private String riskLevel;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 乐观锁版本号（P1-12） */
    @Version
    private Integer version;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
