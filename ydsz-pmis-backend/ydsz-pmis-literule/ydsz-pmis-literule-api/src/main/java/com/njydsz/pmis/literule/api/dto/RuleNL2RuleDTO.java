package com.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 自然语言转规则请求体 DTO
 *
 * <p>用于 {@code /rules/ai/nl2rule} 接口，调用 LLM 将自然语言描述转为
 * 结构化规则定义（含表达式、严重度、描述）。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Schema(description = "AI 自然语言转规则请求体")
public class RuleNL2RuleDTO {

    /**
     * 自然语言描述
     */
    @Schema(description = "自然语言描述", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "金额超过 1 万的紧急订单触发风控审批")
    @NotBlank(message = "{validation.project.msg_d4b5c6d5}")
    private String naturalLanguage;
}
