package com.njydsz.pmis.project.dto.execution;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购申请 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class PurchaseCreateDTO {
    private String purchaseCode;
    private String initiationId;
    private String vendor;
    private String itemName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private LocalDate purchaseDate;
    private String applicantId;
    private String applicantName;
    private String description;
}
