package com.njydsz.pmis.execution.entity;

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
 * WBS 任务
 */
@Data
@TableName("pmis_execution_wbs_task")
public class WbsTaskDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskCode;
    private String taskName;
    private Long initiationId;
    private Long parentId;
    private Integer taskLevel;
    private String wbsPath;
    private Integer sortOrder;
    private String taskType;          // TASK/MILESTONE/SUMMARY
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;
    private Integer durationDays;
    private BigDecimal plannedEffort;
    private BigDecimal actualEffort;
    private BigDecimal progressPct;
    private Long ownerId;
    private String ownerName;
    private String assigneeIds;
    private String priority;          // LOW/NORMAL/HIGH/URGENT
    private String status;            // WbsTaskStatus.code
    private String dependsOn;
    private Integer milestone;
    private String description;
    private String deliverable;
    private String riskLevel;
    private Long tenantId;
    private String providerTraceId;

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
