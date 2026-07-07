package com.njydsz.pmis.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * AI 辅助生成规则请求体 DTO
 *
 * <p>用于 {@code /rules/ai-generate} 与 {@code /rules/ai-generate-and-save}
 * 接口，接收自然语言描述与可用字段列表，调用 LLM 生成规则定义。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Schema(description = "AI 辅助生成规则请求体")
public class RuleAiGenerateDTO {

    /**
     * 自然语言描述（业务方用自然语言描述规则意图）
     */
    @Schema(description = "自然语言描述", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "金额大于 10000 且级别为紧急的高额订单触发审批")
    @NotBlank(message = "{validation.project.msg_c3a4b5c3}")
    private String description;

    /**
     * 可用字段列表（可选，为空时由 LLM 自行推断）
     */
    @Schema(description = "可用字段列表")
    private List<String> availableFields;
}
