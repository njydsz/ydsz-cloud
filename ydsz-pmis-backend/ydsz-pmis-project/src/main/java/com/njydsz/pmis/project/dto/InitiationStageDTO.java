package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 立项阶段迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "立项阶段变更")
public class InitiationStageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "立项 ID", required = true)
    private Long id;

    @NotBlank
    @Schema(description = "目标阶段", required = true,
            allowableValues = {"PRE_INITIATION", "SUBMITTED", "APPROVING",
                    "APPROVED", "REJECTED", "EXECUTING", "CLOSED"})
    private String targetStage;

    @Schema(description = "备注")
    private String remark;
}
