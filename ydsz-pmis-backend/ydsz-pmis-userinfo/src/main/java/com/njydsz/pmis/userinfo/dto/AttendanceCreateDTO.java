package com.njydsz.pmis.userinfo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 出勤登记 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "出勤登记表单")
public class AttendanceCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 员工 ID */
    private Long employeeId;
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
    private String status = "NORMAL";
    /** WORKDAY/WEEKEND/HOLIDAY */
    private String workType = "WORKDAY";
    /** 备注 */
    private String remark;
}
