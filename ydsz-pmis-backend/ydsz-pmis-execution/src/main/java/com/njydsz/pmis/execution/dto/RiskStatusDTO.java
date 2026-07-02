package com.njydsz.pmis.execution.dto;

import lombok.Data;

/**
 * 风险状态变更 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RiskStatusDTO {
    private Long id;
    private String targetStatus;
}
