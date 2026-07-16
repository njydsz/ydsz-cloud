package com.njydsz.pmis.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * 合同状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "合同状态变更")
public class ContractStatusDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 合同 ID */
    @NotNull
    @Schema(description = "合同 ID", requiredMode = RequiredMode.REQUIRED)
    private String id;

    /** 目标状态（DRAFT/SUBMITTED/APPROVING/ACTIVE/SUSPENDED/EXPIRED/TERMINATED） */
    @NotBlank
    @Schema(description = "目标状态", requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"DRAFT", "SUBMITTED", "APPROVING", "ACTIVE",
                    "SUSPENDED", "EXPIRED", "TERMINATED"})
    private String targetStatus;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
