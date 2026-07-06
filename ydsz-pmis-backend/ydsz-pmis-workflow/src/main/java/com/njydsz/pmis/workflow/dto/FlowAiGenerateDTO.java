package com.njydsz.pmis.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 一句话生成流程请求体 DTO
 *
 * <p>用于 {@code /api/v1/workflow/ai/generate} 接口，接收自然语言流程描述，
 * 调用 AI Agent 生成 BPMN 2.0 XML 流程定义。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 一句话生成流程请求体")
public class FlowAiGenerateDTO {

    /**
     * 流程自然语言描述
     */
    @Schema(description = "流程自然语言描述", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "请假审批：直属领导审批 → 部门经理审批（3天以上）→ 人事备案")
    @NotBlank(message = "{validation.workflow.msg_d4b5c6d6}")
    private String description;
}
