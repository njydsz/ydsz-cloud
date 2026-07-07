package com.njydsz.pmis.project.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 利润快照 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ProfitSnapshotDTO {
    private String initiationId;
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
