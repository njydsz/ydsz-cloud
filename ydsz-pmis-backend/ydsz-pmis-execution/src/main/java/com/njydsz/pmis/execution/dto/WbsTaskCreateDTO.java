package com.njydsz.pmis.execution.dto;

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
    private String taskCode;
    private String taskName;
    private Long initiationId;
    private Long parentId;
    private Integer taskLevel;
    private Integer sortOrder;
    private String taskType;          // TASK/MILESTONE/SUMMARY
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private Integer durationDays;
    private BigDecimal plannedEffort;
    private Long ownerId;
    private String ownerName;
    private String assigneeIds;
    private String priority;
    private String dependsOn;
    private Integer milestone;
    private String description;
    private String deliverable;
    private String riskLevel;
}
