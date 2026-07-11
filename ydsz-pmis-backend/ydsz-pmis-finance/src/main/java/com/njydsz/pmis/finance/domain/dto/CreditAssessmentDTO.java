package com.njydsz.pmis.finance.domain.dto;

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

    @NotNull(message = "{validation.execution.msg_6de1fd36}")
    private String customerId;

    private String customerName;

    private String evaluator;

    /** 可选：手工调整基础分 */
    private Integer baseScore;
}
