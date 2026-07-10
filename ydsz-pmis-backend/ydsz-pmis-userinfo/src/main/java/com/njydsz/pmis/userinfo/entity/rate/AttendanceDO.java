package com.njydsz.pmis.userinfo.entity.rate;

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
 * 出勤记录实体
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_attendance")
public class AttendanceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 员工 ID */
    private String employeeId;
    /** 员工姓名 */
    private String employeeName;
    /** 考勤日期 */
    private LocalDate attendanceDate;
    /** 签到时间 */
    private LocalDateTime checkInTime;
    /** 签退时间 */
    private LocalDateTime checkOutTime;
    /** 工时（小时） */
    private BigDecimal workHours;
    /** 加班工时（小时） */
    private BigDecimal overtimeHours;
    /** NORMAL/LATE/EARLY/ABSENT/LEAVE/OVERTIME */
    private String status;
    /** WORKDAY/WEEKEND/HOLIDAY */
    private String workType;
    /** 备注 */
    private String remark;
    /** 租户 ID */
    private String tenantId;
    /** 外部考勤提供方链路追踪 ID */
    private String providerTraceId;
}
