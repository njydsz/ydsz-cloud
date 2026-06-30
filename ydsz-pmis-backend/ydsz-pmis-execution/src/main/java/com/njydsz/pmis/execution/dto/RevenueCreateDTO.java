package com.njydsz.pmis.execution.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收入确认 DTO
 */
@Data
public class RevenueCreateDTO {
    private String revenueCode;
    private Long contractId;
    private Long initiationId;
    private String recognitionMethod;  // MILESTONE/PERCENTAGE/PERCENT_COMPLETE/POINTS/MANUAL
    private String period;
    private BigDecimal amount;
    private LocalDate recognitionDate;
    private String milestone;
    private BigDecimal percentComplete;
    private String description;
}
