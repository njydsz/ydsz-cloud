package com.njydsz.pmis.literule.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则批量优先级调整请求体 DTO
 *
 * <p>用于 {@code /rules/batch-priority} 接口，批量调整规则优先级。
 * {@code delta} 为增量（可为负），最终优先级钳制在 0-100 范围。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "规则批量优先级调整请求体")
public class RuleBatchPriorityDTO {

    /**
     * 规则编码列表
     */
    @Schema(description = "规则编码列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "{validation.project.msg_e5c6d7e5}")
    private List<String> ruleCodes;

    /**
     * 优先级增量（可为负，最终优先级钳制 0-100）
     */
    @Schema(description = "优先级增量（可为负）", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "{validation.project.msg_a1e2f3a2}")
    private Integer delta;
}
