package com.njydsz.pmis.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 请假申请 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "请假申请表单")
public class LeaveCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long employeeId;
    private String employeeName;
    /** ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/OTHER */
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal leaveDays;
    private String reason;
    private String attachmentUrl;
}
