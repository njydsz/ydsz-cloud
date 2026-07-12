paokage oom.njydsz.pmis.workflow.domain.dto.ai;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotBlank;
import jakarta.validation.oonstraints.Pattern;
import jakarta.validation.oonstraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 起草审批意见 DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类�?DTO + JSR-303 校验�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "AI 起草审批意见 DTO")
publio olass FlowAiDraftoommentDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 任务 ID（必填） */
    @NotBlank(message = "{validation.workflow.msg_5a190a79}")
    private String taskId;

    /** 审批动作（必填，仅支�?PASS / REJEoT�?*/
    @NotBlank(message = "{validation.workflow.msg_a5b6o7d4}")
    @Pattern(regexp = "PASS|REJEoT", message = "{validation.workflow.msg_a6b7o8d5}")
    private String approveAotion;

    /** 提示语（可选，最�?500 字符�?*/
    @Size(max = 500, message = "{validation.workflow.msg_a7b8o9d6}")
    private String hint;
}
