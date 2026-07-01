package com.njydsz.pmis.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资源分配创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ResourceAssignmentCreateDTO {

    /** 分配编号 */
    @NotBlank(message = "分配编号不能为空")
    private String assignmentCode;

    /** 员工 ID */
    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String levelCode;

    /** 资源池 ID */
    private Long poolId;
    /** 冗余池类型便于查询 */
    private String poolType;

    /** 关联项目 ID */
    private Long initiationId;
    /** 关联项目名称 */
    private String initiationName;
    /** 关联商机 ID（预占时） */
    private Long opportunityId;

    /** 业务动作：RESERVE/START/TRANSFER/RELEASE/CANCEL */
    @NotBlank(message = "业务动作不能为空")
    private String action;

    /** 投入占比 (0-1) */
    private BigDecimal allocation;
    /** 计划开始日期 */
    private LocalDate plannedStartDate;
    /** 计划结束日期 */
    private LocalDate plannedEndDate;
    /** 实际开始日期 */
    private LocalDate actualStartDate;
    /** 实际结束日期 */
    private LocalDate actualEndDate;
    /** 1=可计费 */
    private Integer billable;
    /** 每日投入工时 */
    private BigDecimal dailyHours;
}
