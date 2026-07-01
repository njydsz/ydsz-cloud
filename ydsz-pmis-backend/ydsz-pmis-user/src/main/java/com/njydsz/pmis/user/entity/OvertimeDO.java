package com.njydsz.pmis.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班申请实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_overtime")
public class OvertimeDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 加班单号 */
    private String overtimeCode;
    /** 员工 ID */
    private Long employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 加班日期 */
    private LocalDate overtimeDate;
    /** 开始时间 */
    private LocalDateTime startTime;
    /** 结束时间 */
    private LocalDateTime endTime;
    /** 加班工时（小时） */
    private BigDecimal overtimeHours;
    /** WORKDAY/WEEKEND/HOLIDAY */
    private String overtimeType;
    /** 1.5/2.0/3.0 倍 */
    private BigDecimal payRate;
    /** 加班事由 */
    private String reason;
    /** 审批单 ID */
    private Long approvalId;
    /** 审批状态（LeaveStatus.code） */
    private String approvalStatus;
    /** 审批人 ID */
    private Long approverId;
    /** 审批人姓名 */
    private String approverName;
    /** 审批时间 */
    private LocalDateTime approvalTime;
    /** 审批意见 */
    private String approvalRemark;
    /** 租户 ID */
    private Long tenantId;
    /** 外部提供方链路追踪 ID */
    private String providerTraceId;
}
