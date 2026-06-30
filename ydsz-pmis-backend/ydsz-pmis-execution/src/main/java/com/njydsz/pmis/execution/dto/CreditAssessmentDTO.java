package com.njydsz.pmis.execution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客户信用评估 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class CreditAssessmentDTO {

    @NotNull(message = "客户 ID 不能为空")
    private Long customerId;

    private String customerName;

    private String evaluator;

    /** 可选：手工调整基础分 */
    private Integer baseScore;
}
