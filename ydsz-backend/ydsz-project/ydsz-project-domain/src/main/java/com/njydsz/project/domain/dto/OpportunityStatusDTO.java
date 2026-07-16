package com.njydsz.project.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

/**
 * 商机状态迁移 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "商机状态变更")
public class OpportunityStatusDTO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 商机 ID */
    @NotNull
    @Schema(description = "商机 ID", requiredMode = RequiredMode.REQUIRED)
    private String id;

    /** 目标状态（FOLLOWING/QUOTED/NEGOTIATING/WON/LOST/INVALID） */
    @NotBlank
    @Schema(description = "目标状态", requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"FOLLOWING", "QUOTED", "NEGOTIATING", "WON", "LOST", "INVALID"})
    private String targetStatus;

    /** 输单原因（LOST 时必填） */
    @Schema(description = "输单原因（LOST 时必填）")
    private String lostReason;
}
