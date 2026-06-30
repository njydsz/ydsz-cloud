package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 合同变更申请 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "合同变更申请")
public class ContractChangeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "合同 ID", required = true)
    private Long contractId;

    @NotBlank
    @Schema(description = "变更编号", required = true)
    private String changeCode;

    @NotBlank
    @Schema(description = "变更类型 SCOPE/AMOUNT/TERM/PERSONNEL/PROGRESS", required = true)
    private String changeType;

    @Schema(description = "变更原因")
    private String changeReason;

    @Schema(description = "变更前值")
    private String beforeValue;

    @Schema(description = "变更后值")
    private String afterValue;

    @Schema(description = "金额变化")
    private BigDecimal amountDelta;

    @Schema(description = "影响分析")
    private String impactAnalysis;
}
