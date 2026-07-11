package com.njydsz.pmis.userinfo.domain.entity.resource;

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

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编号 */
    private String assignmentCode;
    /** 员工 ID */
    private String employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 职级编码 */
    private String levelCode;
    /** 资源池 ID */
    private String poolId;
    /** 冗余池类型便于查询 */
    private String poolType;

    /** 关联项目 ID */
    private String initiationId;
    /** 关联项目名称 */
    private String initiationName;
    /** 关联商机 ID（预占时） */
    private String opportunityId;

    /** 分配状态（AssignmentStatus.code） */
    private String status;
    /** 投入占比 (0-1, e.g. 0.5 半人力) */
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

    /** 租户 ID */
    private String tenantId;
    /** 外部提供方链路追踪 ID */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识：0=未删除，1=已删除 */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
