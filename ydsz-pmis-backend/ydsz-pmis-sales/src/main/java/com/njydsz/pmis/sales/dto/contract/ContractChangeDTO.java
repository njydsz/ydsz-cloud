package com.njydsz.pmis.sales.dto.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 合同 ID */
    @NotNull
    @Schema(description = "合同 ID", requiredMode = RequiredMode.REQUIRED)
    private String contractId;

    /** 变更编号 */
    @NotBlank
    @Schema(description = "变更编号", requiredMode = RequiredMode.REQUIRED)
    private String changeCode;

    /** 变更类型（SCOPE/AMOUNT/TERM/PERSONNEL/PROGRESS） */
    @NotBlank
    @Schema(description = "变更类型 SCOPE/AMOUNT/TERM/PERSONNEL/PROGRESS", requiredMode = RequiredMode.REQUIRED)
    private String changeType;

    /** 变更原因 */
    @Schema(description = "变更原因")
    private String changeReason;

    /** 变更前值 */
    @Schema(description = "变更前值")
    private String beforeValue;

    /** 变更后值 */
    @Schema(description = "变更后值")
    private String afterValue;

    /** 金额变化 */
    @Schema(description = "金额变化")
    private BigDecimal amountDelta;

    /** 影响分析 */
    @Schema(description = "影响分析")
    private String impactAnalysis;
}
