package com.njydsz.pmis.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 起草审批意见 DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类型 DTO + JSR-303 校验。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "AI 起草审批意见 DTO")
public class FlowAiDraftCommentDTO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    /** 任务 ID（必填） */
    @NotBlank(message = "{validation.workflow.msg_5a190a79}")
    private String taskId;

    /** 审批动作（必填，仅支持 PASS / REJECT） */
    @NotBlank(message = "{validation.workflow.msg_a5b6c7d4}")
    @Pattern(regexp = "PASS|REJECT", message = "{validation.workflow.msg_a6b7c8d5}")
    private String approveAction;

    /** 提示语（可选，最大 500 字符） */
    @Size(max = 500, message = "{validation.workflow.msg_a7b8c9d6}")
    private String hint;
}
