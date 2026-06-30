package com.njydsz.pmis.user.entity;

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
 * 资源分配记录
 *
 * <p>覆盖预占(商机阶段) → 入场 → 调岗 → 离场 全生命周期。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_resource_assignment")
public class ResourceAssignmentDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String assignmentCode;     // 业务编号
    private Long employeeId;
    private String employeeName;
    private String levelCode;
    private Long poolId;
    private String poolType;           // 冗余池类型便于查询

    private Long initiationId;         // 关联项目
    private String initiationName;
    private Long opportunityId;        // 关联商机（预占时）

    private String status;             // AssignmentStatus
    private BigDecimal allocation;     // 投入占比 (0-1, e.g. 0.5 半人力)
    private LocalDate plannedStartDate;
    private LocalDate plannedEndDate;
    private LocalDate actualStartDate;
    private LocalDate actualEndDate;

    private Integer billable;          // 1=可计费
    private BigDecimal dailyHours;     // 每日投入工时

    private Long tenantId;
    private String providerTraceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
