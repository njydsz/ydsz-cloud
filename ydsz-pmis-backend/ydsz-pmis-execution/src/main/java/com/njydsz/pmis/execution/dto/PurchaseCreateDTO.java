package com.njydsz.pmis.execution.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购申请 DTO
 */
@Data
public class PurchaseCreateDTO {
    private String purchaseCode;
    private Long initiationId;
    private String vendor;
    private String itemName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private LocalDate purchaseDate;
    private Long applicantId;
    private String applicantName;
    private String description;
}
