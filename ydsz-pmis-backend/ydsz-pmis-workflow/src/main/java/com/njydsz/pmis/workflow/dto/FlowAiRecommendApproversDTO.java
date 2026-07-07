package com.njydsz.pmis.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 推荐审批人 DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类型 DTO + JSR-303 校验。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 推荐审批人 DTO")
public class FlowAiRecommendApproversDTO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 任务 ID（必填） */
    @NotBlank(message = "{validation.workflow.msg_5a190a79}")
    private String taskId;

    /** 业务上下文（可选，最大 1000 字符） */
    @Size(max = 1000, message = "{validation.workflow.msg_a4b5c6d3}")
    private String context;
}
