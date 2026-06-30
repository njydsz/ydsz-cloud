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

    private Long employeeId;
    private String employeeName;
    private LocalDate overtimeDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal overtimeHours;
    /** WORKDAY/WEEKEND/HOLIDAY */
    private String overtimeType;
    /** 1.5/2.0/3.0 倍 */
    private BigDecimal payRate = new BigDecimal("1.5");
    private String reason;
}
