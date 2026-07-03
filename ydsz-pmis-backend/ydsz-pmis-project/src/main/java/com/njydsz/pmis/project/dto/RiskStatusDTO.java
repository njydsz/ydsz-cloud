package com.njydsz.pmis.project.dto;

import lombok.Data;

/**
 * 风险状态变更 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class RiskStatusDTO {
    /** 风险ID */
    private Long id;
    /** 目标状态：RiskStatus.code */
    private String targetStatus;
}
