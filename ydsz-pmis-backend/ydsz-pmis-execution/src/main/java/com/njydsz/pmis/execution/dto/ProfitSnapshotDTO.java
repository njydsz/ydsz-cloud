package com.njydsz.pmis.execution.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 利润快照 DTO
 */
@Data
public class ProfitSnapshotDTO {
    private Long initiationId;
    private String period;            // YYYY-MM
    private BigDecimal contractAmount;
    private BigDecimal recognizedRevenue;
    private BigDecimal billedAmount;
    private BigDecimal receivedAmount;
    private BigDecimal laborCost;
    private BigDecimal purchaseCost;
    private BigDecimal expenseCost;
    private BigDecimal outsourceCost;
    private BigDecimal allocationCost;
    private BigDecimal progressPct;
    private BigDecimal billableHours;
    private BigDecimal nonBillableHours;
}
