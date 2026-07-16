package com.njydsz.literule.api.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则审批委托请求体 DTO（P1-3 多级审批流）
 *
 * <p>用于 {@code /rules/{ruleCode}/delegate} 接口，将当前级别的审批权委托给他人。
 *
 * @author ydsz-team
 * @since 1.7.0
 */
@Data
@Schema(description = "规则审批委托请求体")
public class RuleDelegateDTO {

    /**
     * 被委托人工号（必填）
     */
    @Schema(description = "被委托人工号", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "U002")
    @NotBlank(message = "{validation.project.msg_d4b5c6d4}")
    private String delegatedTo;

    /**
     * 委托说明（可选）
     */
    @Schema(description = "委托说明")
    private String comment;
}
