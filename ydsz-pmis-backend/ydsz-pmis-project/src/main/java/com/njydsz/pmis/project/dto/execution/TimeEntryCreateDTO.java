package com.njydsz.pmis.project.dto.execution;

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
    private String employeeId;
    private String employeeName;
    private String levelCode;
    private String initiationId;
    private String initiationName;
    private String taskId;
    private String taskName;
    private BigDecimal hours;
    private BigDecimal overtime;
    private String workType;
    private String description;
    /** 费率卡 ID（可选，前端不传由后端自动匹配） */
    private String rateId;
    /** 人天费率（可选，前端只读展示，由后端自动匹配填入） */
    private BigDecimal rate;
}
