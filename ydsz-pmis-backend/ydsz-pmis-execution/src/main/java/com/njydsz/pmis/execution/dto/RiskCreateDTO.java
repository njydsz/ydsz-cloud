package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 风险登记 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RiskCreateDTO {
    private String riskCode;
    private Long initiationId;
    private String riskTitle;
    private String riskType;
    private String description;
    private String probability;   // LOW/MEDIUM/HIGH
    private String impact;
    private String mitigation;
    private String contingency;
    private Long ownerId;
    private String ownerName;
}
