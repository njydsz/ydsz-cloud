package com.njydsz.pmis.execution.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费用报销 DTO
 */
@Data
public class ExpenseCreateDTO {
    private String expenseCode;
    private Long initiationId;
    private Long employeeId;
    private String employeeName;
    private String expenseType;  // TRAVEL/CATERING/...
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String description;
    private String receiptUrl;
}
