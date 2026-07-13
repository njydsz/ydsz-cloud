package com.njydsz.pmis.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * 立项阶段迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "立项阶段变更")
public class InitiationStageDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 立项 ID */
    @Schema(description = "立项 ID", requiredMode = RequiredMode.REQUIRED)
    private String id;

    /** 目标阶段（PRE_INITIATION/SUBMITTED/APPROVING/APPROVED/REJECTED/EXECUTING/CLOSED） */
    @NotBlank
    @Schema(description = "目标阶段", requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"PRE_INITIATION", "SUBMITTED", "APPROVING",
                    "APPROVED", "REJECTED", "EXECUTING", "CLOSED"})
    private String targetStage;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
