package com.njydsz.pmis.sales.dto.initiation;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 立项 ID */
    @NotNull
    @Schema(description = "立项 ID", requiredMode = RequiredMode.REQUIRED)
    private String initiationId;

    /** 门径编码（CD1/CD2/CD3/CD4/CD5） */
    @NotBlank
    @Schema(description = "门径编码: CD1/CD2/CD3/CD4/CD5", requiredMode = RequiredMode.REQUIRED)
    private String gateCode;

    /** 评审结果（PASSED/REJECTED/CONDITIONAL） */
    @NotBlank
    @Schema(description = "评审结果: PASSED/REJECTED/CONDITIONAL", requiredMode = RequiredMode.REQUIRED)
    private String reviewResult;

    /** 决策依据 */
    @Schema(description = "决策依据")
    private String decisionBasis;

    /** 附加条件（CONDITIONAL 时使用） */
    @Schema(description = "附加条件（CONDITIONAL 时使用）")
    private String conditions;
}
