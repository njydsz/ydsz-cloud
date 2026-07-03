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
 * 工时录入
 *
 * <p>员工按日填报工时，关联项目与 WBS 任务，经审批后用于成本归集与可计费利用率统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_execution_time_entry")
public class TimeEntryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 填报日期 */
    private LocalDate entryDate;
    /** 员工ID */
    private Long employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String levelCode;
    /** 项目立项ID */
    private Long initiationId;
    /** 项目名称 */
    private String initiationName;
    /** WBS 任务ID */
    private Long taskId;
    /** 任务名称 */
    private String taskName;
    /** 工时（小时） */
    private BigDecimal hours;
    /** 人天数 */
    private BigDecimal days;
    /** 加班工时 */
    private BigDecimal overtime;
    /** 工作类型 */
    private String workType;
    /** 是否可计费：1 是 / 0 否 */
    private Integer billable;
    /** 工作描述 */
    private String description;
    /** 状态：TimeEntryStatus.code */
    private String status;
    /** 审批人ID */
    private Long approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 驳回原因 */
    private String rejectReason;
    /** 租户ID */
    private Long tenantId;
    /** 链路追踪ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：1 已删除 / 0 未删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
