package com.njydsz.pmis.project.dto.execution;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * WBS 任务创建 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class WbsTaskCreateDTO {
    /** 任务编号 */
    private String taskCode;
    /** 任务名称 */
    private String taskName;
    /** 项目立项ID */
    private String initiationId;
    /** 父任务ID（null 表示根节点） */
    private String parentId;
    /** 任务层级（从 1 开始） */
    private Integer taskLevel;
    /** 同级排序号 */
    private Integer sortOrder;
    /** 任务类型：TASK/MILESTONE/SUMMARY */
    private String taskType;          // TASK/MILESTONE/SUMMARY
    /** 计划开始日期 */
    private LocalDate plannedStartDate;
    /** 计划结束日期 */
    private LocalDate plannedEndDate;
    /** 工期（天） */
    private Integer durationDays;
    /** 计划工时（人天） */
    private BigDecimal plannedEffort;
    /** 责任人ID */
    private String ownerId;
    /** 责任人姓名 */
    private String ownerName;
    /** 派单人员ID列表（逗号分隔） */
    private String assigneeIds;
    /** 优先级：LOW/NORMAL/HIGH/URGENT */
    private String priority;
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
}
