package com.njydsz.pmis.finance.domain.dto;

import com.njydsz.pmis.common.safe.annotation.Xss;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 费用报销 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ExpenseCreateDTO {
    private String expenseCode;
    private String initiationId;
    private String employeeId;
    private String employeeName;
    private String expenseType;  // TRAVEL/CATERING/...
    private BigDecimal amount;
    private LocalDate expenseDate;
    @Xss(message = "费用说明包含非法字符")
    private String description;
    private String receiptUrl;
}
