package com.njydsz.pmis.project.dto.ruleengine;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 规则状态变更请求体 DTO
 *
 * <p>用于 {@code /rules/{ruleCode}/status} 接口，切换规则生命周期状态
 * （DRAFT / REVIEW / PUBLISHED / ARCHIVED 等）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Schema(description = "规则状态变更请求体")
public class RuleStatusChangeDTO {

    /**
     * 目标状态（RuleStatus 枚举名，如 PUBLISHED / ARCHIVED）
     */
    @Schema(description = "目标状态（RuleStatus 枚举名）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "PUBLISHED")
    @NotBlank(message = "{validation.project.msg_8304cf7d}")
    private String targetStatus;

    /**
     * 变更备注（审批意见/驳回理由等，可选）
     */
    @Schema(description = "变更备注")
    private String comment;
}
