package com.njydsz.pmis.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班申请 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "加班申请表单")
public class OvertimeCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private BigDecimal payRate = new BigDecimal("1.5");
    /** 加班事由 */
    private String reason;
}
