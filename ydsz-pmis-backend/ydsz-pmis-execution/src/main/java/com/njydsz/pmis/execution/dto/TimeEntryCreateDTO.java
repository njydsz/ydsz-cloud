package com.njydsz.pmis.execution.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工时录入 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class TimeEntryCreateDTO {
    private LocalDate entryDate;
    private Long employeeId;
    private String employeeName;
    private String levelCode;
    private Long initiationId;
    private String initiationName;
    private Long taskId;
    private String taskName;
    private BigDecimal hours;
    private BigDecimal overtime;
    private String workType;
    private String description;
}
