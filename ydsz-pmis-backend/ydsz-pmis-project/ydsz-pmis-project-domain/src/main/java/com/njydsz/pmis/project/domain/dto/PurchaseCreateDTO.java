package com.njydsz.pmis.project.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

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
