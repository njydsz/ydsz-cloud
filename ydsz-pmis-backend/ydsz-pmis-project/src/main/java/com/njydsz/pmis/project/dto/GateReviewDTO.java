package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门径评审 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "门径评审决策")
public class GateReviewDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "立项 ID", required = true)
    private Long initiationId;

    @NotBlank
    @Schema(description = "门径编码: CD1/CD2/CD3/CD4/CD5", required = true)
    private String gateCode;

    @NotBlank
    @Schema(description = "评审结果: PASSED/REJECTED/CONDITIONAL", required = true)
    private String reviewResult;

    @Schema(description = "决策依据")
    private String decisionBasis;

    @Schema(description = "附加条件（CONDITIONAL 时使用）")
    private String conditions;
}
