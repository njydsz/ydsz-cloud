package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商机状态迁移 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "商机状态变更")
public class OpportunityStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "商机 ID", required = true)
    private Long id;

    @NotBlank
    @Schema(description = "目标状态", required = true,
            allowableValues = {"FOLLOWING", "QUOTED", "NEGOTIATING", "WON", "LOST", "INVALID"})
    private String targetStatus;

    @Schema(description = "输单原因（LOST 时必填）")
    private String lostReason;
}
