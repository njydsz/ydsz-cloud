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

    @TableId(type = IdType.AUTO)
    private Long id;

    private String overtimeCode;
    private Long employeeId;
    private String employeeName;
    private LocalDate overtimeDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal overtimeHours;
    /** WORKDAY/WEEKEND/HOLIDAY */
    private String overtimeType;
    /** 1.5/2.0/3.0 倍 */
    private BigDecimal payRate;
    private String reason;
    private Long approvalId;
    private String approvalStatus;
    private Long approverId;
    private String approverName;
    private LocalDateTime approvalTime;
    private String approvalRemark;
    private Long tenantId;
    private String providerTraceId;
}
